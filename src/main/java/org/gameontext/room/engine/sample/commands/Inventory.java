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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.gameontext.room.engine.Room;
import org.gameontext.room.engine.User;
import org.gameontext.room.engine.meta.ItemDesc;
import org.gameontext.room.engine.parser.CommandHandler;
import org.gameontext.room.engine.parser.CommandTemplate;
import org.gameontext.room.engine.parser.ParsedCommand;
import org.gameontext.room.engine.parser.Node.Type;

public class Inventory extends CommandHandler {

    private static final CommandTemplate inventory = new CommandTemplateBuilder().build(Type.VERB, "Inventory").build();

    private static final Set<CommandTemplate> templates = Collections
            .unmodifiableSet(new HashSet<CommandTemplate>(Arrays.asList(new CommandTemplate[] { inventory })));

    @Override
    public String getHelpText(){
        return "List the items in your inventory.";
    }

    @Override
    public Set<CommandTemplate> getTemplates() {
        return templates;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void processCommand(Room room, String execBy, ParsedCommand command) {
        User u = room.getUserById(execBy);
        if (u != null) {
            if (u.inventory.isEmpty()) {
                room.playerEvent(execBy, "You do not appear to be carrying anything.", null);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("You are carrying; ");
                boolean first = true;
                for (ItemDesc item : u.inventory) {
                    if (!first)
                        sb.append(", ");
                    sb.append(item.name);
                    first = false;
                }
                room.playerEvent(execBy, sb.toString(), null);
            }
        }
    }

    @Override
    public void processUnknown(Room room, String execBy, String origCmd, String cmdWithoutVerb) {
        room.playerEvent(execBy, "I'm sorry, but I'm not sure how I'm supposed to inventory " + cmdWithoutVerb, null);
    }

}
