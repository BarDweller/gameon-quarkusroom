package org.gameontext.room.registration;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gameontext.room.Log;
import org.gameontext.room.engine.Engine;
import org.gameontext.room.engine.Room;
import org.gameontext.room.engine.meta.DoorDesc;
import org.gameontext.room.engine.meta.ExitDesc;
import org.gameontext.signed.SignedClientRequestFilter;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.runtime.annotations.RegisterForReflection;

@Startup
@Singleton
@RegisterForReflection(ignoreNested = false, classNames = {"org.glassfish.json.JsonStringImpl", 
                                                           "org.glassfish.json.JsonNumberImpl"})
public class RoomRegistrationHandler {

    @ConfigProperty(name = "MAP_KEY", defaultValue = "x")
    String secret;
    @ConfigProperty(name = "SYSTEM_ID", defaultValue = "x")
    String mapuserid;
    @ConfigProperty( name = "RECROOM_SERVICE_URL", defaultValue = "x")
    String endPoint;
    @ConfigProperty( name = "MAP_SERVICE_URL", defaultValue = "x")
    String mapLocation;
    @ConfigProperty( name = "MAP_HEALTH_SERVICE_URL", defaultValue = "x")
    String mapHealth;

    CopyOnWriteArraySet<Room> roomsToRegister = new CopyOnWriteArraySet<>();
    boolean schedLog = false;

    RoomRegistrationHandler(){          
    }

    private void logConfig(){
        Log.log(Level.FINE, this, "RoomRegistrationHandler constructed with config :");
        Log.log(Level.FINE, this, " - SYSTEM_ID="+mapuserid);
        Log.log(Level.FINE, this, " - RECROOM_SERVICE_URL="+endPoint);
        Log.log(Level.FINE, this, " - MAP_SERVICE_URL="+mapLocation);
        Log.log(Level.FINE, this, " - MAP_HEALTH_SERVICE_URL="+mapHealth);
        Log.log(Level.FINE, this, " - MAP_KEY(length)="+secret.length());
    }

    private static class RegistrationResult {
        enum Type { NOT_REGISTERED, REGISTERED, SERVICE_UNAVAILABLE };
        public Type type;
        public JsonObject registeredObject;

        RegistrationResult() {}
        RegistrationResult(Type type) {
            this.type = type;
        }
    }
    private static RegistrationResult MAP_UNAVAILABLE = new RegistrationResult(RegistrationResult.Type.SERVICE_UNAVAILABLE);

    @PostConstruct
    void registerRooms(){
        Engine e = Engine.getEngine();
        for(Room room : e.getRooms()){
            roomsToRegister.add(room);
        }
    }

    @Scheduled(every = "15s", skipExecutionIf = RegistrationRequiredCheck.class)
    void attemptRegistration(){
        if(!schedLog){ logConfig(); schedLog=true; }
        Log.log(Level.FINE, this, "Scheduled registration invoked currently "+roomsToRegister.size()+" rooms remaining.");
        Set<Room> successful = new HashSet<Room>();
        for(Room r : roomsToRegister){
            Log.log(Level.FINE, this, " - Checking registration for room "+r.getRoomId());
            RegistrationResult rr = checkExistingRegistration(r);
            Log.log(Level.FINE, this, " - Room "+r.getRoomId()+" had status of "+rr.type.toString());
            switch(rr.type){
                case REGISTERED:{
                    RegistrationResult updatedRegistration = compareRoomAndUpdateIfRequired(r, rr.registeredObject);
                    if(updatedRegistration.type == RegistrationResult.Type.REGISTERED){
                        updateRoomWithExits(r, updatedRegistration.registeredObject);
                        successful.add(r);
                    }
                    break;
                }
                case NOT_REGISTERED:{
                    RegistrationResult newRegistration = registerOrUpdateRoom(Mode.REGISTER, r, null);
                    if(newRegistration.type == RegistrationResult.Type.REGISTERED){
                        updateRoomWithExits(r, newRegistration.registeredObject);
                        successful.add(r);
                    }
                    break;
                }
                case SERVICE_UNAVAILABLE:{
                    //we'll be called again in 10s, and maybe the service will work then!
                    break;
                }
            }            
        }
        Log.log(Level.FINE, this, "Scheduled registration removing "+successful.size()+" rooms from registration set");
        roomsToRegister.removeAll(successful);
        Log.log(Level.FINE, this, "Scheduled registration completing with "+roomsToRegister.size()+" rooms remaining.");
    }

    /**
     * Obtain a jaxrs client configured appropriately for ssl to map.
     */
    private Client getClient(){

        //Build client that uses the SSLContext defined by 'DefaultSSLSettings' in server.xml
        Client client = ClientBuilder.newBuilder()
                                       .build();
        // add our request signer
        client.register(new SignedClientRequestFilter(mapuserid, secret));
  
        return client;
      }

    private boolean mapIsHealthy(){
        try {
            Client cleanClient = ClientBuilder.newBuilder().build();  
            Response response = cleanClient.target(mapHealth)
                                    .request(MediaType.APPLICATION_JSON)
                                    .get();
            response.close();
            int code = response.getStatusInfo().getStatusCode();

            Log.log(Level.FINE, this, "Checked map service health : {0}", Integer.toString(code));
            return code == 200;
        } catch ( Exception e ) {
            Log.log(Level.SEVERE, this, "Error checking map service health : {0}", e.toString());
            return false;
        }
    }

        /**
     * Obtain current registration for this room
     * @param roomId
     * @return
     */
    private RegistrationResult checkExistingRegistration(Room room) {

        // If map service isn't healthy yet, don't even bother (but make sure
        // to try again later!)
        if ( !mapIsHealthy() ) {
            return MAP_UNAVAILABLE;
        }

        RegistrationResult result = new RegistrationResult();
        try {
            Client queryClient = getClient();

            // create the jax-rs 2.0 client
            WebTarget queryRoot = queryClient.target(mapLocation);

            // add the lookup arg for this room..
            WebTarget target = queryRoot.queryParam("owner", mapuserid).queryParam("name", room.getRoomId());
            Response r = null;

            r = target.request(MediaType.APPLICATION_JSON).get(); // .accept(MediaType.APPLICATION_JSON).get();
            int code = r.getStatusInfo().getStatusCode();
            switch (code) {
                case 204: {
                    // room is unknown to map
                    result.type = RegistrationResult.Type.NOT_REGISTERED;
                    return result;
                }
                case 200: {
                    // request succeeded.. we need to parse the result into a JsonObject..
                    // query url always returns an array, so we need to reach in to obtain our
                    // hit. There should only ever be the one, becase we searched by owner and
                    // name, and rooms should be unique by owner & name;
                    String respString = r.readEntity(String.class);
                    JsonReader reader = Json.createReader(new StringReader(respString));
                    JsonArray resp = reader.readArray();
                    JsonObject queryResponse = resp.getJsonObject(0);

                    //get the id for our already-registered room.
                    String roomId = queryResponse.getString("_id");

                    // now we have our id.. make a new request to get our exit wirings..
                    queryClient = getClient();

                    WebTarget lookup = queryClient.target(mapLocation);
                    Invocation.Builder builder = lookup.path("{roomId}").resolveTemplate("roomId", roomId).request(MediaType.APPLICATION_JSON);
                    Response response = builder.get();
                    respString = response.readEntity(String.class);

                    //Log.log(Level.FINE, this, "EXISTING_INFO({0})({1}):{2}", mapuserid, room.getRoomId(), respString);

                    reader = Json.createReader(new StringReader(respString));
                    queryResponse = reader.readObject();

                    //save the full response with exit info into the result var.
                    result.type = RegistrationResult.Type.REGISTERED;
                    result.registeredObject = queryResponse;
                    return result;
                }
                case 404:// fall through to 503.
                case 503: {
                    Log.log(Level.FINE, this,"Room "+room.getRoomId()+" had rc of "+code+" when queried.");
                    result.type = RegistrationResult.Type.SERVICE_UNAVAILABLE;
                    return result;
                }
                default: {
                    throw new Exception("Unknown response code from map " + code);
                }
            }
        } catch (Exception e){
            Log.log(Level.SEVERE, this, "Exception occurred during room query for "+room.getRoomId(), e);
            result.type = RegistrationResult.Type.SERVICE_UNAVAILABLE;
            return result;
        }
    }


    private RegistrationResult compareRoomAndUpdateIfRequired(Room room, JsonObject registeredRoom){
        JsonObject info = registeredRoom.getJsonObject("info");

        boolean needsUpdate = true;
        if(   room.getRoomId().equals(info.getString("name"))
           && room.getRoomName().equals(info.getString("fullName"))
           && room.getRoomDescription().equals(info.getString("description"))
                )
        {
            //all good so far =)
            JsonObject doors = info.getJsonObject("doors");
            int count = room.getDoors().size();
            if(doors!=null && doors.size()==count){
                for(DoorDesc door : room.getDoors()){
                    String description = doors.getString(door.direction.toString().toLowerCase());
                    if(description.equals(door.description)){
                        count--;
                    }
                }
            }else{
                Log.log(Level.INFO,this,"Door count mismatch.");
            }
            //if all the doors matched.. lets check the connection details..
            if(count==0){
                JsonObject connectionDetails = info.getJsonObject("connectionDetails");
                if(connectionDetails!=null){
                    if("websocket".equals(connectionDetails.getString("type"))
                       && getEndpointForRoom(room).equals(connectionDetails.getString("target"))
                       ){

                        //all good.. no need to update this one.
                        needsUpdate = false;

                    }else{
                        Log.log(Level.INFO,this,"ConnectionDetails mismatch.");
                    }
                }else{
                    Log.log(Level.INFO,this,"ConnectionDetails absent.");
                }
            }else{
                Log.log(Level.INFO,this,"Doors content mismatch.");
            }
        }else{
            Log.log(Level.INFO,this,"Basic room compare failed.");
        }

        if(needsUpdate){
            Log.log(Level.INFO,this,"Update required for {0}",room.getRoomId());
            return registerOrUpdateRoom(Mode.UPDATE,room,registeredRoom.getString("_id"));
        }else{
            Log.log(Level.INFO,this,"Room {0} is still up to date in Map, no update required.",room.getRoomId());
            RegistrationResult r = new RegistrationResult();
            r.type = RegistrationResult.Type.REGISTERED;
            r.registeredObject = registeredRoom;
            return r;
        }
    }


    enum Mode {REGISTER,UPDATE};
    private RegistrationResult registerOrUpdateRoom(Mode mode, Room room, String roomId){
        Client postClient = getClient();

        // create the jax-rs 2.0 client
        WebTarget root = postClient.target(mapLocation);

        // build the registration/update payload (post data)
        JsonObjectBuilder registrationPayload = Json.createObjectBuilder();
        // add the basic room info.
        registrationPayload.add("name", room.getRoomId());
        registrationPayload.add("fullName", room.getRoomName());
        registrationPayload.add("description", room.getRoomDescription());
        // add the doorway descriptions we'd like the game to use if it
        // wires us to other rooms.
        JsonObjectBuilder doors = Json.createObjectBuilder();
        for(DoorDesc door : room.getDoors()){
            switch(door.direction){
                case NORTH:{
                    doors.add("n",door.description);
                    break;
                }
                case SOUTH:{
                    doors.add("s",door.description);
                    break;
                }
                case EAST:{
                    doors.add("e",door.description);
                    break;
                }
                case WEST:{
                    doors.add("w",door.description);
                    break;
                }
                case UP:{
                    doors.add("u",door.description);
                    break;
                }
                case DOWN:{
                    doors.add("d",door.description);
                    break;
                }
            }
        }
        registrationPayload.add("doors", doors.build());

        // add the connection info for the room to connect back to us..
        JsonObjectBuilder connInfo = Json.createObjectBuilder();
        connInfo.add("type", "websocket"); // the only current supported type.
        connInfo.add("target", getEndpointForRoom(room));
        registrationPayload.add("connectionDetails", connInfo.build());

        JsonObject objectPayload = registrationPayload.build();
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = Json.createWriter(stringWriter);) {
            jsonWriter.writeObject(objectPayload);
        }catch(Exception e){
            Log.log(Level.SEVERE, this, "Error writing json object : {0}", objectPayload);
            // Unable to connect to map w/in reasonable time
            return MAP_UNAVAILABLE;            
        }
        String jsonPayload = stringWriter.toString();

        Log.log(Level.FINER,this,"Registration Payload: "+jsonPayload);

        Response response=null;
        try {
            switch(mode){
                case REGISTER:{
                    Invocation.Builder builder = root.request(MediaType.APPLICATION_JSON);
                    response = builder.post(Entity.json(jsonPayload));
                    break;
                }
                case UPDATE:{
                    Invocation.Builder builder = root.path("{roomId}").resolveTemplate("roomId", roomId).request(MediaType.APPLICATION_JSON);
                    response = builder.put(Entity.json(jsonPayload));
                    break;
                }
                default:{
                    throw new IllegalStateException("Bad enum value "+mode.name());
                }
            }
        } catch (RuntimeException pe) {
            Log.log(Level.SEVERE, this, "Error registering room provider : {0}", pe.toString());
            // Unable to connect to map w/in reasonable time
            return MAP_UNAVAILABLE;
        }

        RegistrationResult r = new RegistrationResult();
        try {

            if ( (mode.equals(Mode.REGISTER) && Status.CREATED.getStatusCode() == response.getStatus()) ||
                 (mode.equals(Mode.UPDATE) && Status.OK.getStatusCode() == response.getStatus()) ){
                String regString = response.readEntity(String.class);
                JsonReader reader = Json.createReader(new StringReader(regString));
                JsonObject registrationResponse = reader.readObject();

                r.type = RegistrationResult.Type.REGISTERED;
                r.registeredObject = registrationResponse;
                Log.log(Level.INFO,this,"Sucessful registration/update operation against ({0})({1})({2}) : {3}",roomId,mapuserid,room.getRoomId(),regString);
            } else {
                String resp = response.readEntity(String.class);
                Log.log(Level.SEVERE,this,"Error registering room provider : "+room.getRoomName()+" : status code "+response.getStatus()+": response "+String.valueOf(resp));
                r.type = RegistrationResult.Type.NOT_REGISTERED;
                //registration will be reattempted in 10s
            }
        } finally {
            response.close();
        }
        return r;
    }



    private void updateRoomWithExits(Room room, JsonObject registeredObject) {
        JsonObject exits = registeredObject.getJsonObject("exits");
        Map<String,ExitDesc> exitMap = new HashMap<String,ExitDesc>();
        for(Entry<String, JsonValue> e : exits.entrySet()){
            JsonObject j = (JsonObject)e.getValue();
            //can be null, eg when linking back to firstroom
            JsonObject c = j.getJsonObject("connectionDetails");
            ExitDesc exit = new ExitDesc(e.getKey(),
                    j.getString("name"),
                    j.getString("fullName"),
                    j.getString("door"),
                    j.getString("_id"),
                    c!=null?c.getString("type"):null,
                    c!=null?c.getString("target"):null);
            exitMap.put(e.getKey(), exit);
            Log.log(Level.FINER, this, "Added exit {0} to {1} : {2}", e.getKey(), room.getRoomId(), exit);
        }
        room.setExits(exitMap);
    }

    public String getEndpointForRoom(Room room) {
        //note, this is the _external_ path to reach this room, 
        //      which may be different from the 'getPathFragmentForRoom'
        //      when this service is behind a path forwarding proxy
        return endPoint + "/ws/" + room.getRoomId();
    }
}

