/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package org.gameontext.room.engine.sample.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gameontext.room.engine.Room;
import org.gameontext.room.engine.User;
import org.gameontext.room.engine.meta.ItemDesc;
import org.gameontext.room.engine.parser.CommandHandler;
import org.gameontext.room.engine.parser.CommandTemplate;
import org.gameontext.room.engine.parser.Item;
import org.gameontext.room.engine.parser.ParsedCommand;
import org.gameontext.room.engine.parser.Node.Type;

public class Look extends CommandHandler {

    private static final CommandTemplate look = new CommandTemplateBuilder().build(Type.VERB, "Look").build();
    private static final CommandTemplate lookAtRoomItem = new CommandTemplateBuilder().build(Type.VERB, "Look")
            .build(Type.LINKWORD, "AT").build(Type.ROOM_ITEM).build();
    private static final CommandTemplate lookAtInventoryItem = new CommandTemplateBuilder().build(Type.VERB, "Look")
            .build(Type.LINKWORD, "AT").build(Type.INVENTORY_ITEM).build();
    private static final CommandTemplate lookInContainerItem = new CommandTemplateBuilder().build(Type.VERB, "Look")
            .build(Type.LINKWORD, "IN").build(Type.CONTAINER_ITEM).build();
    private static final CommandTemplate lookAtItemInContainer = new CommandTemplateBuilder().build(Type.VERB, "Look")
            .build(Type.LINKWORD, "AT").build(Type.ITEM_INSIDE_CONTAINER_ITEM).build();

    private static final Set<CommandTemplate> templates = Collections
            .unmodifiableSet(new HashSet<CommandTemplate>(Arrays.asList(new CommandTemplate[] { look, lookAtRoomItem,
                    lookAtInventoryItem, lookInContainerItem, lookAtItemInContainer })));

    @Override
    public String getHelpText(){
        return "Look at the room, **at** an item, or **in** a container.";
    }

    @Override
    public Set<CommandTemplate> getTemplates() {
        return templates;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public void processCommand(Room room, String execBy, ParsedCommand command) {
        String key = command.key;
        User u = room.getUserById(execBy);
        if (u != null) {
            if (key.equals(look.key)) {
                List<String> invItems = new ArrayList<String>();
                List<String> roomItems = new ArrayList<String>();
                for (ItemDesc i : room.getItems()) {
                    roomItems.add(i.name);
                }
                for (ItemDesc i : u.inventory) {
                    invItems.add(i.name);
                }
                Map<String,String> commands = new HashMap<String,String>();
                for(CommandHandler ch : room.getCommands()){
                    if(!ch.isHidden()){
                        String verb = ch.getTemplates().iterator().next().template.get(0).data.toLowerCase();
                        commands.put("/"+verb, ch.getHelpText());
                    }
                }
                room.locationEvent(execBy, room, room.getRoomDescription(), room.getExits(), roomItems, invItems,commands);
            } else if (key.equals(lookAtRoomItem.key) || key.equals(lookAtInventoryItem.key)
                    || key.equals(lookAtItemInContainer.key)) {
                Item i = (Item) command.args.get(1);
                room.command(execBy, "Examine " + i.item.name);
            } else if (key.equals(lookInContainerItem.key)) {
                // we could treat this differently if we wanted to handle 'look
                // in container' differently from 'look at container'
                Item i = (Item) command.args.get(1);
                room.command(execBy, "Examine " + i.item.name);
            }
        }
    }

    @Override
    public void processUnknown(Room room, String execBy, String origCmd, String cmdWithoutVerb) {
        room.playerEvent(execBy, "I'm sorry, but I'm not sure how I'm supposed to look " + cmdWithoutVerb, null);
    }

}
