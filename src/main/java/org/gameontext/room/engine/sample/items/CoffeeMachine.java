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
package org.gameontext.room.engine.sample.items;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.CDI;

import org.gameontext.room.Log;
import org.gameontext.room.engine.Room;
import org.gameontext.room.engine.User;
import org.gameontext.room.engine.meta.ItemDesc;
import org.gameontext.room.engine.parser.CommandTemplate;
import org.gameontext.room.engine.parser.Item;
import org.gameontext.room.engine.parser.ItemUseHandler;
import org.gameontext.room.engine.parser.ParsedCommand;
import org.gameontext.room.engine.parser.Node.Type;

import java.util.logging.Level;

public class CoffeeMachine extends ItemDesc {

    public static final ItemUseHandler handler = new ItemUseHandler() {
        private final CommandTemplate useCoffeeMachine = new CommandTemplateBuilder().build(Type.ROOM_ITEM).build();
        private final CommandTemplate useCoffeeMachineWithInventoryMug = new CommandTemplateBuilder()
                .build(Type.ROOM_ITEM).build(Type.LINKWORD, "With").build(Type.INVENTORY_ITEM).build();
        private final CommandTemplate useCoffeeMachineWithRoomMug = new CommandTemplateBuilder().build(Type.ROOM_ITEM)
                .build(Type.LINKWORD, "With").build(Type.ROOM_ITEM).build();
        private final Set<CommandTemplate> templates = Collections
                .unmodifiableSet(new HashSet<CommandTemplate>(Arrays.asList(new CommandTemplate[] { useCoffeeMachine,
                        useCoffeeMachineWithInventoryMug, useCoffeeMachineWithRoomMug })));

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
                if (key.equals(useCoffeeMachine.key)) {
                    room.playerEvent(execBy,
                            "You randomly press buttons on the coffee machine, hot liquid spills all over the floor, you mop it up, you decide that's probably not how this machine is supposed to be used.",
                            u.username
                                    + " uses the coffee machine, spilling coffee everywhere, then quietly mops it up while mumbling about reading instruction manuals");
                } else if (key.equals(useCoffeeMachineWithInventoryMug.key)) {
                    Item i = (Item) command.args.get(2);
                    if (i.item == Items.mug) {
                        if (i.item.getAndSetState("empty", "full") || i.item.getAndSetState("", "full")) {
                            room.playerEvent(execBy, "You make a hot cup of coffee.",
                                    u.username + " makes a mug of coffee.");
                        } else {
                            room.playerEvent(execBy,
                                    "You attempt to fill the already full cup with more coffee. Coffee goes everywhere, you desperately clean up the coffee hoping nobody noticed.",
                                    u.username + " spills coffee all over the floor, then cleans it up.");
                        }
                    } else {
                        room.playerEvent(execBy, "You try several times to get the Coffee Machine to interact with the "
                                + i.item.name + " but can't seem to figure out how.", null);
                    }
                } else if (key.equals(useCoffeeMachineWithRoomMug.key)) {
                    Item i = (Item) command.args.get(2);
                    if (i.item == Items.mug) {
                        room.playerEvent(execBy,
                                "You try to telepathically make the mug interact with the coffee machine, and fail. Perhaps you should take the mug first?",
                                null);
                    } else {
                        room.playerEvent(execBy, "You try several times to get the Coffee Machine to interact with the "
                                + i.item.name + " but can't seem to figure out how.", null);
                    }
                } else {
                  System.out.println("Unknown key passed "+key+ " fyi: "+useCoffeeMachineWithInventoryMug);
                }
            }
        }

        @Override
        public void processUnknown(Room room, String execBy, String origCmd, String cmdWithoutVerb) {
            room.playerEvent(execBy,
                    "I'm not sure that using the " + cmdWithoutVerb + " is such a great idea. I'll skip that.", null);
        }
    };

    public CoffeeMachine() {
        super("Coffee Machine", "A machine for making coffee, it appears to be functional.", false, false, handler);
    }
}
