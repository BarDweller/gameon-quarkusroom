package org.gameontext.room.websocket;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.websocket.Endpoint;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import org.gameontext.room.Log;
import org.gameontext.room.engine.Engine;
import org.gameontext.room.engine.Room;
import org.gameontext.room.engine.Room.RoomResponseProcessor;
import org.gameontext.signed.SignedRequestHmac;
import org.gameontext.signed.SignedRequestMap;

/**
 * Manages the registration of all rooms in the Engine with the concierge
 */
public class WebSocketManager implements ServerApplicationConfig {

    private Engine e = Engine.getEngine();

    public static class SessionRoomResponseProcessor
            implements RoomResponseProcessor {
        private Collection<Session> activeSessions = new CopyOnWriteArraySet<Session>();
        private AtomicInteger counter = new AtomicInteger(0);

        private void generateEvent(Session session, JsonObject content, String userID, boolean selfOnly, int bookmark)
                throws IOException {
            JsonObjectBuilder response = Json.createObjectBuilder();
            response.add("type", "event");
            response.add("content", content);
            response.add("bookmark", bookmark);

            String msg = "player," + (selfOnly ? userID : "*") + "," + response.build().toString();
            Log.log(Level.FINE, this, "ROOM(PE): sending to session {0} messsage {1}", session.getId(), msg);
            if(session.isOpen())session.getAsyncRemote().sendText(msg);
        }

        @Override
        public void playerEvent(String senderId, String selfMessage, String othersMessage) {
            // System.out.println("Player message :: from("+senderId+")
            // onlyForSelf("+String.valueOf(selfMessage)+")
            // others("+String.valueOf(othersMessage)+")");
            JsonObjectBuilder content = Json.createObjectBuilder();
            boolean selfOnly = true;
            if (othersMessage != null && othersMessage.length() > 0) {
                content.add("*", othersMessage);
                selfOnly = false;
            }
            if (selfMessage != null && selfMessage.length() > 0) {
                content.add(senderId, selfMessage);
            }
            JsonObject json = content.build();
            int count = counter.incrementAndGet();
            for (Session s : activeSessions) {
                try {
                    generateEvent(s, json, senderId, selfOnly, count);
                } catch (IOException io) {
                    throw new RuntimeException(io);
                }
            }
        }

        private void generateRoomEvent(Session session, JsonObject content, int bookmark) {
            JsonObjectBuilder response = Json.createObjectBuilder();
            response.add("type", "event");
            response.add("content", content);
            response.add("bookmark", bookmark);

            String msg = "player,*," + response.build().toString();

            Log.log(Level.FINE, this, "ROOM(RE): sending to session {0} messsage {1}", session.getId(), msg);
            if(session.isOpen())session.getAsyncRemote().sendText(msg);
        }

        @Override
        public void roomEvent(String s) {
            // System.out.println("Message sent to everyone :: "+s);
            JsonObjectBuilder content = Json.createObjectBuilder();
            content.add("*", s);
            JsonObject json = content.build();
            int count = counter.incrementAndGet();
            for (Session session : activeSessions) {
                generateRoomEvent(session, json, count);
            }
        }

        public void chatEvent(String username, String msg) {
            JsonObjectBuilder content = Json.createObjectBuilder();
            content.add("type", "chat");
            content.add("username", username);
            content.add("content", msg);
            content.add("bookmark", counter.incrementAndGet());
            JsonObject json = content.build();
            for (Session session : activeSessions) {
                String cmsg = "player,*," + json.toString();
                Log.log(Level.FINE, this, "ROOM(CE): sending to session {0} messsage {1}", session.getId(), cmsg);
                if(session.isOpen())session.getAsyncRemote().sendText(cmsg);
            }
        }

        @Override
        public void locationEvent(String senderId, String roomId, String roomName, String roomDescription, Map<String,String> exits,
                List<String> objects, List<String> inventory, Map<String,String> commands) {
            JsonObjectBuilder content = Json.createObjectBuilder();
            content.add("type", "location");
            content.add("name", roomId);
            content.add("fullName", roomName);
            content.add("description", roomDescription);

            JsonObjectBuilder exitJson = Json.createObjectBuilder();
            for (Entry<String, String> e : exits.entrySet()) {
                exitJson.add(e.getKey().toUpperCase(), e.getValue());
            }
            content.add("exits", exitJson.build());

            JsonObjectBuilder commandJson = Json.createObjectBuilder();
            for (Entry<String, String> c : commands.entrySet()) {
                commandJson.add(c.getKey(), c.getValue());
            }
            content.add("commands", commandJson.build());

            JsonArrayBuilder inv = Json.createArrayBuilder();
            for (String i : inventory) {
                inv.add(i);
            }
            content.add("pockets", inv.build());

            JsonArrayBuilder objs = Json.createArrayBuilder();
            for (String o : objects) {
                objs.add(o);
            }
            content.add("objects", objs.build());
            content.add("bookmark", counter.incrementAndGet());

            JsonObject json = content.build();
            for (Session session : activeSessions) {
                String lmsg = "player," + senderId + "," + json.toString();
                Log.log(Level.FINE, this, "ROOM(LE): sending to session {0} messsage {1}", session.getId(), lmsg);
                if(session.isOpen())session.getAsyncRemote().sendText(lmsg);
            }
        }

        @Override
        public void exitEvent(String senderId, String message, String exitID, String exitJson) {
            JsonObjectBuilder content = Json.createObjectBuilder();
            content.add("type", "exit");
            content.add("exitId", exitID);
            content.add("content", message);
            content.add("bookmark", counter.incrementAndGet());
            JsonObject json = content.build();
            for (Session session : activeSessions) {
                String emsg = "playerLocation," + senderId + "," + json.toString();
                Log.log(Level.FINE, this, "ROOM(EE): sending to session {0} messsage {1}", session.getId(), emsg);
                if(session.isOpen())session.getAsyncRemote().sendText(emsg);
            }
        }

        public void addSession(Session s) {
            activeSessions.add(s);
        }

        public void removeSession(Session s) {
            activeSessions.remove(s);
        }

        public Collection<Session> getSessions() {
            return activeSessions;
        }
    }

    private static class RoomWSConfig extends ServerEndpointConfig.Configurator {
        private final Room room;
        private final SessionRoomResponseProcessor srrp;
        private final String token;

        public RoomWSConfig(Room room, SessionRoomResponseProcessor srrp, String token) {
            this.room = room;
            this.srrp = srrp;
            this.room.setRoomResponseProcessor(srrp);
            this.token = token;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getEndpointInstance(Class<T> endpointClass) {
            RoomWS r = new RoomWS(this.room, this.srrp);
            return (T) r;
        }

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            super.modifyHandshake(sec, request, response);

            if ( token == null || token.isEmpty() ) {
                Log.log(Level.FINEST, this, "No token set for room, skipping validation");
            } else {
                Log.log(Level.FINEST, this, "Validating WS handshake");
                SignedRequestHmac wsHmac = new SignedRequestHmac("", token, "", request.getRequestURI().getRawPath());

                try {
                    wsHmac.checkHeaders(new SignedRequestMap.MLS_StringMap(request.getHeaders()))
                            .verifyFullSignature()
                            .wsResignRequest(new SignedRequestMap.MLS_StringMap(response.getHeaders()));

                    Log.log(Level.INFO, this, "validated and resigned", wsHmac);
                } catch(Exception e) {
                    Log.log(Level.WARNING, this, "Failed to validate HMAC, unable to establish connection", e);

                    response.getHeaders().replace(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, Collections.emptyList());
                }
            }
        }
    }

    private Set<ServerEndpointConfig> registerRooms(Collection<Room> rooms) {
    
        Set<ServerEndpointConfig> endpoints = new HashSet<ServerEndpointConfig>();
        for (Room room : rooms) {
            //now open our websocket.
            SessionRoomResponseProcessor srrp = new SessionRoomResponseProcessor();
            ServerEndpointConfig.Configurator config = new RoomWSConfig(room, srrp, "");
            String path = "/rooms/ws/"+room.getRoomId();
            endpoints.add(ServerEndpointConfig.Builder.create(RoomWS.class, path)
                    .configurator(config).build());
        }

        return endpoints;
    }

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
        try {
            return registerRooms(e.getRooms());
        } catch (IllegalStateException e) {
            Log.log(Level.SEVERE, this, "Error building endpoint configs for room", e);
            //getEndpointConfigs is defined by ServerApplicationConfig, and doesn't allow for failure..
            //so this is the best we can do..
            throw e;
        }
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        return null;
    }

}
