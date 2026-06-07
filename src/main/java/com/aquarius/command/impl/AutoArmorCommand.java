package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.module.impl.AutoArmor;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class AutoArmorCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("autoArmor")
            .category(CommandCategory.MODULE)
            .description("""
            Automatically equips the best armor in your inventory
            """)
            .usageLines(
                "on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoArmor")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoArmor.enabled = getToggle(c, "toggle");
                MODULE.get(AutoArmor.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("AutoArmor " + toggleStrCaps(CONFIG.client.extra.autoArmor.enabled))
                    .primaryColor();
                return OK;
            }));
    }
}
