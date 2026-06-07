package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;

import static com.aquarius.Globals.LAUNCH_CONFIG;

public class LicenseCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("license")
            .category(CommandCategory.INFO)
            .description("Displays the software license and information about your legal rights")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("license").executes(c -> {
            c.getSource()
                .getEmbed()
                .title("AquariusProxy License Info")
                .description("""
                    AquariusProxy is licensed under [the GNU Affero General Public License](https://www.gnu.org/licenses/agpl-3.0.en.html).
                    
                    This means that you are free to use, modify, and distribute AquariusProxy (including hosting as a free or paid service) as long as:
                    1. You make the source code available to users
                    2. Any modifications you make to AquariusProxy are licensed under the AGPL
                    
                    Compiled releases bundle third party code and data which each remain licensed under their original licenses.
                    
                    Source code for AquariusProxy is available at [the AquariusProxy GitHub repository](%s).
                    """.formatted(sourceUrl()))
                .primaryColor();

        });
    }

    /**
     * If you distribute modified versions of AquariusProxy that do not properly configure the launch_config.json, replace this with a link to your repository.
     */
    private String sourceUrl() {
        return "https://github.com/" + LAUNCH_CONFIG.repo_owner + "/" + LAUNCH_CONFIG.repo_name;
    }
}
