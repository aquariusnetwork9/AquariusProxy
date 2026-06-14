package com.aquarius.discord;

import com.aquarius.feature.elytra.Route;
import com.aquarius.module.impl.ElytraTrip;
import com.aquarius.util.config.Config.Client.Extra.ElytraPilot.HighwayDir;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.List;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Discord interactive panel for the trip planner. Two views, toggled by a button (state in
 * {@code CONFIG.client.extra.elytraPilot.tripPanelRouteView}):
 *
 * <ul>
 *   <li><b>Trip</b> — the simple coord/dimension trip: dimension dropdown, typed X/Y/Z + radius modals,
 *       highways / gear-up / start-on-connect toggles, Launch / Cancel.</li>
 *   <li><b>Route builder</b> — manage saved multi-leg routes end-to-end: a route dropdown (select to edit),
 *       New (modal), Add ride / Add fly leg (modal accepting {@code DIR dist} or {@code netherX netherZ}),
 *       Remove last leg, Delete, and Launch. The selected route is {@code tripPanelRoute}.</li>
 * </ul>
 *
 * <p>All controls mutate {@code CONFIG.client.extra.elytraPilot.*} directly (the config IS the panel state).
 * Posted with {@code /fly panel}; shared plumbing lives in {@link DiscordPanel}.
 */
public final class TripPanel extends DiscordPanel {

    private static final String PREFIX = "trip:";
    // simple-trip controls
    private static final String DIM = "trip:dim", COORDS = "trip:coords", RADIUS = "trip:radius",
        HIGHWAYS = "trip:highways", GEARUP = "trip:gearup", STARTCONN = "trip:startconn",
        LAUNCH = "trip:launch", CANCEL = "trip:cancel", ROUTES = "trip:routes",
        MODAL = "trip:coordsmodal", RADIUSMODAL = "trip:radiusmodal";
    // route-builder controls
    private static final String RSEL = "trip:rsel", RNEW = "trip:rnew", RADDRIDE = "trip:raddride",
        RADDFLY = "trip:raddfly", RDELLEG = "trip:rdelleg", RDEL = "trip:rdel", RLAUNCH = "trip:rlaunch",
        REND = "trip:rend", ROWDEST = "trip:rowdest", RBACK = "trip:rback",
        RNEWMODAL = "trip:rnewmodal", RLEGRIDEMODAL = "trip:rlegridemodal", RLEGFLYMODAL = "trip:rlegflymodal",
        ROWDESTMODAL = "trip:rowdestmodal";

    @Override protected String prefix() { return PREFIX; }

    // ---------------------------------------------------------------- render

    @Override
    protected Embed embed() {
        return CONFIG.client.extra.elytraPilot.tripPanelRouteView ? routeEmbed() : tripEmbed();
    }

    private Embed tripEmbed() {
        var c = CONFIG.client.extra.elytraPilot;
        return new Embed()
            .primaryColor()
            .title("Trip Planner")
            .addField("Destination", c.tripTargetX + ", " + c.tripTargetY + ", " + c.tripTargetZ
                + (c.tripTargetIsNether ? "  (nether coords)" : ""), false)
            .addField("Dimension", c.tripTargetIsNether ? "Nether destination" : "Overworld (auto-route)", true)
            .addField("OW-direct radius", c.spawnRegionRadius + "b", true)
            .addField("Nether leg", c.tripUseHighways ? "highways" : "open-nether", true)
            .addField("Gear-up", c.tripGearUp ? "on" : "off", true)
            .addField("Start on connect", c.tripStartOnConnect ? "on" : "off", true)
            .addField("Saved routes", String.valueOf(c.tripRoutes.size()), true)
            .addField("Status", c.tripActive
                ? "🟢 ACTIVE" + (c.tripActiveRoute.isBlank() ? "" : " (route " + c.tripActiveRoute + ")") : "idle", true);
    }

    private Embed routeEmbed() {
        var c = CONFIG.client.extra.elytraPilot;
        Route r = c.tripRoutes.get(c.tripPanelRoute);
        Embed e = new Embed().primaryColor().title("Trip Planner — Route Builder");
        if (r == null) {
            e.description(c.tripRoutes.isEmpty()
                ? "No saved routes yet. Tap **➕ New** to create one."
                : "Pick a route from the dropdown to edit, or **➕ New** to create one.");
            e.addField("Saved routes", String.valueOf(c.tripRoutes.size()), true);
            return e;
        }
        StringBuilder sb = new StringBuilder();
        int lastX = 0, lastZ = 0;
        if (r.legs().isEmpty()) sb.append("_(no legs yet — add ride/fly legs)_");
        for (int i = 0; i < r.legs().size(); i++) {
            Route.Leg leg = r.legs().get(i);
            sb.append('`').append(i + 1).append(".` ").append(leg.ride() ? "RIDE" : "FLY ")
              .append(" → ").append(leg.x()).append(", ").append(leg.z());
            if (leg.ride()) sb.append(" (y").append(leg.roadY()).append(')');
            sb.append('\n');
            lastX = leg.x(); lastZ = leg.z();
        }
        e.addField("Route '" + r.id() + "'  (" + r.legs().size() + " leg" + (r.legs().size() == 1 ? "" : "s") + ")",
            sb.toString(), false);
        e.addField("Ends", r.endInNether()
            ? "🌋 nether — land at the last leg (" + lastX + ", " + lastZ + ")"
            : "🗺 overworld — portal out to " + r.destX() + ", " + r.destY() + ", " + r.destZ()
                + (r.destX() == 0 && r.destZ() == 0 ? "   ⚠ set it with 🎯 OW dest" : ""), false);
        return e;
    }

    @Override
    protected List<ActionRow> components() {
        return CONFIG.client.extra.elytraPilot.tripPanelRouteView ? routeComponents() : tripComponents();
    }

    private List<ActionRow> tripComponents() {
        var c = CONFIG.client.extra.elytraPilot;
        StringSelectMenu dim = StringSelectMenu.create(DIM)
            .setPlaceholder(c.tripTargetIsNether ? "Dimension: Nether destination" : "Dimension: Overworld")
            .addOption("Overworld (direct, or auto-route via nether)", "ow")
            .addOption("Nether destination (coords are nether)", "nether")
            .build();
        return List.of(
            ActionRow.of(dim),
            ActionRow.of(
                Button.secondary(COORDS, "Set Coordinates"),
                Button.secondary(RADIUS, "Set Radius"),
                Button.secondary(HIGHWAYS, "Highways: " + (c.tripUseHighways ? "ON" : "off")),
                Button.secondary(GEARUP, "Gear-up: " + (c.tripGearUp ? "ON" : "off")),
                Button.secondary(STARTCONN, "Start on connect: " + (c.tripStartOnConnect ? "ON" : "off"))
            ),
            ActionRow.of(
                Button.success(LAUNCH, "🚀 Launch"),
                Button.danger(CANCEL, "🛑 Cancel / Reset"),
                Button.primary(ROUTES, "🧭 Routes (" + c.tripRoutes.size() + ")")
            )
        );
    }

    private List<ActionRow> routeComponents() {
        var c = CONFIG.client.extra.elytraPilot;
        List<ActionRow> rows = new ArrayList<>();
        if (!c.tripRoutes.isEmpty()) {
            var sel = StringSelectMenu.create(RSEL).setPlaceholder("Select a route to edit / launch…");
            int n = 0;
            for (String name : c.tripRoutes.keySet()) {
                if (++n > 25) break;
                int legs = c.tripRoutes.get(name).legs().size();
                sel.addOption(name, name, legs + " leg(s)" + (name.equals(c.tripPanelRoute) ? "  ✓ selected" : ""));
            }
            rows.add(ActionRow.of(sel.build()));
        }
        boolean hasSel = c.tripRoutes.containsKey(c.tripPanelRoute);
        rows.add(ActionRow.of(
            Button.secondary(RNEW, "➕ New"),
            Button.secondary(RADDRIDE, "⬆ Add ride").withDisabled(!hasSel),
            Button.secondary(RADDFLY, "➡ Add fly").withDisabled(!hasSel),
            Button.secondary(RDELLEG, "⬅ Remove last").withDisabled(!hasSel),
            Button.danger(RDEL, "🗑 Delete").withDisabled(!hasSel)
        ));
        boolean nether = hasSel && c.tripRoutes.get(c.tripPanelRoute).endInNether();
        rows.add(ActionRow.of(
            Button.success(RLAUNCH, "🚀 Launch").withDisabled(!hasSel),
            Button.secondary(REND, "🔀 Ending: " + (nether ? "Nether" : "Overworld")).withDisabled(!hasSel),
            Button.secondary(ROWDEST, "🎯 OW dest").withDisabled(!hasSel || nether),
            Button.primary(RBACK, "◂ Back to Trip")
        ));
        return rows;
    }

    // ---------------------------------------------------------------- modals

    private Modal coordsModal() {
        var c = CONFIG.client.extra.elytraPilot;
        TextInput x = TextInput.create("trip:x", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.tripTargetX)).setPlaceholder("e.g. 25000").build();
        TextInput y = TextInput.create("trip:y", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.tripTargetY)).setPlaceholder("landing height, e.g. 100").build();
        TextInput z = TextInput.create("trip:z", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.tripTargetZ)).setPlaceholder("e.g. 0").build();
        return Modal.create(MODAL, "Trip Destination")
            .addComponents(Label.of("X", x), Label.of("Y (landing height)", y), Label.of("Z", z))
            .build();
    }

    private Modal radiusModal() {
        TextInput r = TextInput.create("trip:radiusval", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(CONFIG.client.extra.elytraPilot.spawnRegionRadius))
            .setPlaceholder("blocks from 0,0; beyond this, route via the nether (e.g. 100000)").build();
        return Modal.create(RADIUSMODAL, "Overworld-direct radius")
            .addComponents(Label.of("Radius (blocks from 0,0)", r))   // ≤45 chars: Discord rejects longer modal labels
            .build();
    }

    private Modal newRouteModal() {
        TextInput name = TextInput.create("trip:rname", TextInputStyle.SHORT).setRequired(true)
            .setPlaceholder("route name, e.g. base-a").build();
        return Modal.create(RNEWMODAL, "New Route")
            .addComponents(Label.of("Name", name))
            .build();   // creates an overworld route; flip with 🔀 Ending, set landing with 🎯 OW dest
    }

    private Modal owDestModal() {
        Route r = CONFIG.client.extra.elytraPilot.tripRoutes.get(CONFIG.client.extra.elytraPilot.tripPanelRoute);
        int x = r == null ? 0 : r.destX(), y = r == null ? 64 : r.destY(), z = r == null ? 0 : r.destZ();
        TextInput tx = TextInput.create("trip:odx", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(x)).setPlaceholder("overworld X, e.g. 25000").build();
        TextInput ty = TextInput.create("trip:ody", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(y)).setPlaceholder("landing height, e.g. 100").build();
        TextInput tz = TextInput.create("trip:odz", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(z)).setPlaceholder("overworld Z, e.g. 0").build();
        return Modal.create(ROWDESTMODAL, "Overworld landing")
            .addComponents(Label.of("X", tx), Label.of("Y (landing height)", ty), Label.of("Z", tz))
            .build();
    }

    private Modal legModal(boolean ride) {
        TextInput dest = TextInput.create("trip:ldest", TextInputStyle.SHORT).setRequired(true)
            .setPlaceholder("'W 619000' (heading+dist)  or  '-619000 0' (nether x z)").build();
        Modal.Builder b = Modal.create(ride ? RLEGRIDEMODAL : RLEGFLYMODAL, ride ? "Add Ride Leg" : "Add Fly Leg");
        if (ride) {
            TextInput roadY = TextInput.create("trip:lroady", TextInputStyle.SHORT).setRequired(false)
                .setPlaceholder("road Y (blank = 120)").build();
            b.addComponents(Label.of("Destination", dest), Label.of("Road Y", roadY));
        } else {
            b.addComponents(Label.of("Destination", dest));
        }
        return b.build();
    }

    // ---------------------------------------------------------------- interaction handlers

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var c = CONFIG.client.extra.elytraPilot;
        switch (e.getComponentId()) {
            // --- simple trip view ---
            case COORDS -> { e.replyModal(coordsModal()).queue(); return false; }
            case RADIUS -> { e.replyModal(radiusModal()).queue(); return false; }
            case HIGHWAYS -> c.tripUseHighways = !c.tripUseHighways;
            case GEARUP -> c.tripGearUp = !c.tripGearUp;
            case STARTCONN -> c.tripStartOnConnect = !c.tripStartOnConnect;
            case ROUTES -> c.tripPanelRouteView = true;
            case LAUNCH -> {
                c.tripActiveRoute = "";          // the Launch button runs the panel-configured coord trip, not a route
                c.tripActive = true;
                MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("🚀 Trip launched to " + c.tripTargetX + ", " + c.tripTargetY + ", "
                    + c.tripTargetZ + (c.tripTargetIsNether ? " (nether)" : "")).queue();
            }
            case CANCEL -> {
                c.tripActive = false;
                MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
            }
            // --- route builder view ---
            case RBACK -> c.tripPanelRouteView = false;
            case RNEW -> { e.replyModal(newRouteModal()).queue(); return false; }
            case RADDRIDE -> {
                if (!c.tripRoutes.containsKey(c.tripPanelRoute)) { e.reply("Select or create a route first.").setEphemeral(true).queue(); return false; }
                e.replyModal(legModal(true)).queue(); return false;
            }
            case RADDFLY -> {
                if (!c.tripRoutes.containsKey(c.tripPanelRoute)) { e.reply("Select or create a route first.").setEphemeral(true).queue(); return false; }
                e.replyModal(legModal(false)).queue(); return false;
            }
            case RDELLEG -> {
                Route r = c.tripRoutes.get(c.tripPanelRoute);
                if (r != null && !r.legs().isEmpty()) r.legs().remove(r.legs().size() - 1);
            }
            case RDEL -> {
                c.tripRoutes.remove(c.tripPanelRoute);
                c.tripPanelRoute = "";
            }
            case REND -> {
                Route r = c.tripRoutes.get(c.tripPanelRoute);
                if (r == null) { e.reply("Select or create a route first.").setEphemeral(true).queue(); return false; }
                c.tripRoutes.put(r.id(), new Route(r.id(), !r.endInNether(), r.destX(), r.destY(), r.destZ(), r.legs()));
            }
            case ROWDEST -> {
                if (!c.tripRoutes.containsKey(c.tripPanelRoute)) { e.reply("Select or create a route first.").setEphemeral(true).queue(); return false; }
                e.replyModal(owDestModal()).queue(); return false;
            }
            case RLAUNCH -> {
                Route r = c.tripRoutes.get(c.tripPanelRoute);
                if (r == null || r.legs().isEmpty()) { e.reply("Route is empty — add legs first.").setEphemeral(true).queue(); return false; }
                c.tripActiveRoute = c.tripPanelRoute;
                c.tripActive = true;
                MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("🚀 Route '" + c.tripPanelRoute + "' launched.").queue();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        var c = CONFIG.client.extra.elytraPilot;
        if (DIM.equals(e.getComponentId())) {
            c.tripTargetIsNether = "nether".equals(e.getValues().get(0));
            return true;
        }
        if (RSEL.equals(e.getComponentId())) {
            c.tripPanelRoute = e.getValues().get(0);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        var c = CONFIG.client.extra.elytraPilot;
        if (MODAL.equals(e.getModalId())) {
            try {
                c.tripTargetX = Integer.parseInt(e.getValue("trip:x").getAsString().trim());
                c.tripTargetY = Integer.parseInt(e.getValue("trip:y").getAsString().trim());
                c.tripTargetZ = Integer.parseInt(e.getValue("trip:z").getAsString().trim());
            } catch (Exception ex) {
                e.reply("Invalid coordinates — use whole numbers.").setEphemeral(true).queue();
                return false;
            }
            return true;
        }
        if (RADIUSMODAL.equals(e.getModalId())) {
            try {
                int r = Integer.parseInt(e.getValue("trip:radiusval").getAsString().trim());
                if (r < 0) throw new NumberFormatException();
                c.spawnRegionRadius = r;
            } catch (Exception ex) {
                e.reply("Invalid radius — use a whole number ≥ 0.").setEphemeral(true).queue();
                return false;
            }
            return true;
        }
        if (RNEWMODAL.equals(e.getModalId())) {
            String name = e.getValue("trip:rname").getAsString().trim();
            if (name.isEmpty()) { e.reply("Name is required.").setEphemeral(true).queue(); return false; }
            c.tripRoutes.put(name, new Route(name, false, 0, 64, 0, new ArrayList<>()));   // overworld by default
            c.tripPanelRoute = name;
            return true;
        }
        if (ROWDESTMODAL.equals(e.getModalId())) {
            Route r = c.tripRoutes.get(c.tripPanelRoute);
            if (r == null) { e.reply("No route selected.").setEphemeral(true).queue(); return false; }
            try {
                int x = Integer.parseInt(e.getValue("trip:odx").getAsString().trim());
                int y = Integer.parseInt(e.getValue("trip:ody").getAsString().trim());
                int z = Integer.parseInt(e.getValue("trip:odz").getAsString().trim());
                c.tripRoutes.put(r.id(), new Route(r.id(), r.endInNether(), x, y, z, r.legs()));
            } catch (NumberFormatException nf) {
                e.reply("Coordinates must be whole numbers.").setEphemeral(true).queue();
                return false;
            }
            return true;
        }
        if (RLEGRIDEMODAL.equals(e.getModalId()) || RLEGFLYMODAL.equals(e.getModalId())) {
            boolean ride = RLEGRIDEMODAL.equals(e.getModalId());
            Route r = c.tripRoutes.get(c.tripPanelRoute);
            if (r == null) { e.reply("No route selected.").setEphemeral(true).queue(); return false; }
            int[] xz = parseLegDest(r, e.getValue("trip:ldest").getAsString());
            if (xz == null) {
                e.reply("Destination must be 'DIR dist' (e.g. `W 619000`) or 'x z' (e.g. `-619000 0`).").setEphemeral(true).queue();
                return false;
            }
            int roadY = 120;
            if (ride) {
                String ry = e.getValue("trip:lroady").getAsString().trim();
                if (!ry.isEmpty()) {
                    try { roadY = Integer.parseInt(ry); }
                    catch (NumberFormatException nf) { e.reply("Road Y must be a whole number.").setEphemeral(true).queue(); return false; }
                }
            }
            r.legs().add(new Route.Leg(ride, xz[0], xz[1], roadY));
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers

    /** Parse a leg destination field: {@code "<DIR> <dist>"} (heading from the route's running tail) or
     *  {@code "<netherX> <netherZ>"} (absolute). Returns the endpoint nether {x,z}, or null if unparseable. */
    private static int[] parseLegDest(Route r, String s) {
        String[] t = s.trim().split("\\s+");
        if (t.length != 2) return null;
        try {
            if (isDir(t[0])) {
                int dist = Integer.parseInt(t[1]);
                int px = r.legs().isEmpty() ? 0 : r.legs().get(r.legs().size() - 1).x();
                int pz = r.legs().isEmpty() ? 0 : r.legs().get(r.legs().size() - 1).z();
                double[] u = unitVec(HighwayDir.valueOf(t[0].toUpperCase()));
                return new int[]{ px + (int) Math.round(u[0] * dist), pz + (int) Math.round(u[1] * dist) };
            }
            return new int[]{ Integer.parseInt(t[0]), Integer.parseInt(t[1]) };
        } catch (NumberFormatException nf) {
            return null;
        }
    }

    private static boolean isDir(String s) {
        for (HighwayDir d : HighwayDir.values()) if (d.name().equalsIgnoreCase(s)) return true;
        return false;
    }

    private static double[] unitVec(HighwayDir d) {
        double s = 0.7071067811865476;
        return switch (d) {
            case N -> new double[]{0, -1};  case S -> new double[]{0, 1};
            case E -> new double[]{1, 0};   case W -> new double[]{-1, 0};
            case NE -> new double[]{s, -s}; case SE -> new double[]{s, s};
            case SW -> new double[]{-s, s}; case NW -> new double[]{-s, -s};
        };
    }
}
