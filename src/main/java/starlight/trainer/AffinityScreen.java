package starlight.trainer;

import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import starlight.trainer.AffinityLayout.Rect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A server-backed three-slot type-resonance (Affinity) picker drawn as a trainer device. The
 * client only asks; every select/replace/remove and link change is validated by the server,
 * which answers with a new snapshot that this screen then shows.
 *
 * <p>Left pane tabs: candidate types (a click adds to the first free slot or takes the type out
 * again), the effects that apply now, the resonance link (one-click suggestions plus the manual
 * chips) and the HUD settings (size, anchor, offset, with a live preview). Right: the three
 * slots (× removes one) and an info box with a live summary or, while a candidate is hovered, a
 * preview of what the click would change. Keys: Enter = done, 1–3 = clear that slot, Tab = next tab.
 */
public final class AffinityScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(
            SuperPalletTowner.MOD_ID, "textures/gui/affinity_device.png");

    public static final int TAB_CANDIDATES = 0;
    public static final int TAB_EFFECTS = 1;
    public static final int TAB_LINK = 2;
    public static final int TAB_HUD = 3;
    private static final int TAB_COUNT = 4;

    private static final int ICON = AffinityView.TYPE_ICON;
    private static final int ROW = ICON + 2;
    /** Text offset that centres a font line in a candidate row. */
    private static final int TEXT_DY = (ROW - 1 - 8) / 2 + 1;
    private static final int MIN_COLUMN = 88;
    private static final int SCROLLBAR = 4;
    private static final int SUGGESTION_ROW = 14;
    private static final int SUGGESTION_ICON = 12;
    private static final int HUD_ROW = 14;
    private static final int TEXT = 0xFFF8F0DE;
    private static final int MUTED = 0xFF9AA6BC;
    private static final int GREEN = 0xFF7EE07E;
    private static final int AMBER = 0xFFFFD45A;
    private static final int RED_TEXT = 0xFFFF8A80;
    private static final int PURPLE = 0xFFE0A8FF;
    private static final int CYAN = 0xFF9BECFF;

    // Tabs: the chosen tab and the page under the tabs share one fill and one border, so the
    // chosen tab reads as the front of the page; the others sit lower, darker and behind its edge.
    private static final int PAGE = 0xFF2B3B60;
    private static final int PAGE_EDGE = 0xFF8EA6D6;
    private static final int TAB_IDLE = 0xFF151C2D;
    private static final int TAB_IDLE_EDGE = 0xFF465878;
    private static final int TAB_HOVER = 0xFF34466F;
    private static final int TAB_HOVER_EDGE = 0xFFD4DEF4;
    /** Inactive tabs start this much lower than the chosen tab. */
    private static final int TAB_DROP = 2;

    /** HUD preview when nothing resonates yet (constant objects so the HUD cache keeps them). */
    private static final List<String> SAMPLE_TYPES = List.of("fire", "water", "grass");
    private static final Set<String> NO_LINKS = Set.of();

    private List<String> available = List.of();
    private List<String> selected = List.of();
    private Set<String> active = Set.of();
    private List<String> link = List.of();
    private Set<String> links = Set.of();
    private List<String> hudTypes = List.of();
    private List<ResonanceText.PartyMember> party = List.of();
    private List<List<String>> partyTypes = List.of();
    private int replacementSlot = -1;
    private int scroll;
    private int tab = TAB_CANDIDATES;
    private int previousTab = TAB_CANDIDATES;
    private int effectScroll;
    private List<FormattedCharSequence> effectLines = List.of();
    private int effectRows;
    private List<LinkSuggestions.Suggestion> suggestions = List.of();
    private int suggestionScroll;
    private List<Component> summaryLines = List.of();

    // Layout, recomputed in init() whenever the window size changes.
    private Rect panel;
    private Rect header;
    private Rect page;
    private Rect slots;
    private final Rect[] tabs = new Rect[TAB_COUNT];
    private final Component[] tabLabels = new Component[TAB_COUNT];
    private boolean shortTabs;
    private int tabBaseline;
    private AffinityView.Grid grid;
    private int slotTop;
    private int slotRow;
    private int slotIcon;
    private boolean slotLabel;
    private AffinityLayout.InfoBox info;
    private Rect hint;
    private AffinityLayout.LinkPage linkPage;
    private Rect[] hudRows = new Rect[0];
    private Button leftButton;
    private Button doneButton;
    private final Map<String, Button> hudControls = new LinkedHashMap<>();
    /** Short confirmation glow, started only after the server accepts a change. */
    private long confirmationUntilMs;
    private boolean confirmationLink;
    /** Slot that just received a type (pops in), and when. -1 when none. */
    private int popSlot = -1;
    private long popSince;
    /** A short message in the info box (why a click did nothing). */
    private long flashUntilMs;
    private Component flash = Component.empty();
    // Hover preview cache.
    private String previewKey = "";
    private List<Component> previewLines = List.of();
    /** Info lines drawn in the last frame, for the harness and the info tooltip. */
    private List<Component> shownInfo = List.of();

    public AffinityScreen(String snapshot) {
        super(Component.translatable("ui.super_pallet_towner.title"));
        readSnapshot(snapshot);
    }

    public void refresh(String snapshot) {
        List<String> previousSelected = selected;
        List<String> previousLink = link;
        readSnapshot(snapshot);
        if (minecraft != null) rebuildWidgets();
        if (!previousSelected.equals(selected) || !previousLink.equals(link)) {
            confirmationLink = previousSelected.equals(selected);
            confirmationUntilMs = net.minecraft.Util.getMillis() + 650L;
            popSlot = -1;
            for (int i = 0; i < selected.size(); i++) {
                if (i >= previousSelected.size() || !selected.get(i).equals(previousSelected.get(i))) {
                    popSlot = i;
                    popSince = net.minecraft.Util.getMillis();
                    break;
                }
            }
            playConfirmSound(confirmationLink, selected.size() >= previousSelected.size() && link.size() >= previousLink.size());
        }
    }

    /** A soft chime once the server accepts a change: higher for additions, lower for removals. */
    private void playConfirmSound(boolean linkChange, boolean added) {
        if (minecraft == null || !HudSettings.animations()) return;
        var sound = linkChange ? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value()
                : net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME;
        float pitch = linkChange ? (added ? 1.6F : 1.1F) : (added ? 1.3F : 0.8F);
        minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, pitch, 0.6F));
    }

    // ------------------------------------------------------------------ harness hooks (read only)

    public List<String> shownCandidates() { return available; }
    public List<String> shownSlots() { return selected; }
    public int scrollOffset() { return scroll; }
    public int maxScroll() { return grid.maxScroll(); }
    public int columns() { return grid.columns(); }

    /** Centre of a candidate row on screen, or {@code null} if it is scrolled out of view. */
    public int[] candidatePoint(String type) {
        int index = available.indexOf(type);
        if (index < 0) return null;
        int row = index / grid.columns() - scroll;
        if (row < 0 || row >= grid.visibleRows()) return null;
        int column = index % grid.columns();
        return new int[] {page.x() + column * columnWidth() + columnWidth() / 2, page.y() + row * ROW + ROW / 2};
    }

    /** Centre of a slot row's name area (a click there picks the slot for replacement). */
    public int[] slotPoint(int slot) {
        return new int[] {slots.x() + slots.w() / 2, slotTop + slot * slotRow + slotRow / 2};
    }

    /** Centre of the × on a slot (a click there removes that slot). */
    public int[] slotRemovePoint(int slot) {
        Rect box = removeBox(slot);
        return new int[] {box.x() + box.w() / 2, box.y() + box.h() / 2};
    }

    public int[] candidatesCentre() {
        return new int[] {page.x() + page.w() / 2, page.y() + page.h() / 2};
    }

    public Set<String> shownActive() { return active; }
    public boolean effectsShown() { return tab == TAB_EFFECTS; }
    public boolean linkShown() { return tab == TAB_LINK; }
    public boolean hudShown() { return tab == TAB_HUD; }
    public int shownTab() { return tab; }
    public int effectScrollOffset() { return effectScroll; }
    public int effectMaxScroll() { return Math.max(0, effectLines.size() - effectRows); }
    public int effectVisibleRows() { return effectRows; }
    public List<String> shownLink() { return link; }
    public Set<String> shownLinks() { return links; }
    public boolean shortTabLabels() { return shortTabs; }
    public int providerCount(String type) { return AffinityUiLogic.providers(partyTypes, type); }

    public int[] tabPoint(boolean effects) {
        return tabPoint(effects ? TAB_EFFECTS : TAB_CANDIDATES);
    }

    public int[] tabPoint(int index) {
        Rect box = tabs[index];
        return new int[] {box.x() + box.w() / 2, box.y() + box.h() / 2};
    }

    /** {x, y, w, h} of each tab, for overlap checks. */
    public int[][] tabBoxes() {
        int[][] result = new int[TAB_COUNT][];
        for (int i = 0; i < TAB_COUNT; i++) result[i] = new int[] {tabs[i].x(), tabs[i].y(), tabs[i].w(), tabs[i].h()};
        return result;
    }

    /** {x, y, w, h} of the page under the tabs. */
    public int[] pageBox() {
        return new int[] {page.x(), page.y(), page.w(), page.h()};
    }

    /** Whether each tab's label is drawn whole (not cut with "…"). */
    public boolean tabLabelsFit() {
        for (int i = 0; i < TAB_COUNT; i++) if (font.width(tabLabels[i]) > tabs[i].w() - 6) return false;
        return true;
    }

    /** Centre of the chip of the type in link slot {@code slot} (the chip toggles it). */
    public int[] linkSlotPoint(int slot) {
        if (slot >= 0 && slot < link.size()) {
            int[] point = linkChipPoint(link.get(slot));
            if (point != null) return point;
        }
        Rect chips = linkPage.chips();
        return new int[] {chips.x() + chips.w() / 2, chips.y() + chips.h() / 2};
    }

    /** Centre of the link chip for a resonance type, or {@code null} if it is not in a resonance slot. */
    public int[] linkChipPoint(String type) {
        int index = selected.indexOf(type);
        if (index < 0) return null;
        Rect chips = linkPage.chips();
        int w = chips.w() / AffinityView.MAX_SLOTS;
        return new int[] {chips.x() + index * w + w / 2, chips.y() + chips.h() / 2};
    }

    public int[] linkTextPoint() {
        return new int[] {page.x() + page.w() / 2, linkPage.statusY() + 4};
    }

    /** Recipe ids of the link suggestions in display order. */
    public List<String> suggestionIds() {
        return suggestions.stream().map(LinkSuggestions.Suggestion::id).toList();
    }

    /** The suggestions with their badge and state, as plain text ("id|signature|state"). */
    public List<String> suggestionStates() {
        return suggestions.stream().map(s -> s.id() + "|" + (s.signature() ? "signature" : "standard") + "|" + s.state())
                .toList();
    }

    /** Centre of a suggestion row, or {@code null} when it is not listed or scrolled away. */
    public int[] suggestionPoint(String id) {
        for (int i = 0; i < suggestions.size(); i++) {
            if (!suggestions.get(i).id().equals(id)) continue;
            int row = i - suggestionScroll;
            if (row < 0 || row >= linkPage.listRows()) return null;
            Rect list = linkPage.list();
            return new int[] {list.x() + list.w() / 2, list.y() + row * SUGGESTION_ROW + SUGGESTION_ROW / 2};
        }
        return null;
    }

    /** Centre of a HUD control ("scale-", "scale+", "anchor<", "anchor>", "x-", "x+", "y-", "y+", "reset"). */
    public int[] hudControlPoint(String name) {
        Button button = hudControls.get(name);
        return button == null ? null : new int[] {button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2};
    }

    public int[] leftButtonPoint() {
        return new int[] {leftButton.getX() + leftButton.getWidth() / 2, leftButton.getY() + leftButton.getHeight() / 2};
    }

    public String leftButtonLabel() {
        return leftButton.getMessage().getString();
    }

    /** The info box lines drawn in the last frame, as plain text. */
    public List<String> infoTexts() {
        return shownInfo.stream().map(Component::getString).toList();
    }

    public String hintText() {
        return hintComponent().getString();
    }

    /** The link page's status text as plain text: the status line, then one line per suggestion. */
    public List<String> linkTexts() {
        List<String> result = new ArrayList<>();
        result.add(linkStatusLine().getString());
        for (var suggestion : suggestions) {
            result.add(ResonanceText.typeList(suggestion.types()).getString() + "：" + summaryOf(suggestion.id()).getString());
        }
        return result;
    }

    /** The active-effects list as plain text, one entry per logical line (before wrapping). */
    public List<String> effectTexts() {
        return ResonanceText.activeEffects(selected, active, party, link, links).stream().map(Component::getString).toList();
    }

    /** The hover tooltip of a candidate type as plain text, one entry per logical line. */
    public List<String> candidateTooltipTexts(String type) {
        return candidateTooltip(type, false).stream().map(Component::getString).toList();
    }

    /** {width, height} of a candidate tooltip once wrapped for the current window, then the window's. */
    public int[] candidateTooltipSize(String type) {
        return tooltipSize(candidateTooltip(type, false));
    }

    /** {width, height, window width, window height} of the Rock + Steel recipe tooltip. */
    public int[] linkTooltipSize() {
        return tooltipSize(ResonanceText.linkTooltip(ResonanceLinks.RECIPES.get(0), null));
    }

    public List<String> linkTooltipTexts() {
        return ResonanceText.linkTooltip(ResonanceLinks.RECIPES.get(0), null).stream().map(Component::getString).toList();
    }

    /**
     * Layout self-check for the harness: text areas, slots, buttons and the hint line stay inside
     * the panel/screen and do not overlap. Empty when everything is fine.
     */
    public List<String> layoutProblems() {
        List<String> problems = new ArrayList<>();
        Rect screen = new Rect(0, 0, width, height);
        if (!panel.inside(screen)) problems.add("panel outside screen " + panel);
        for (int i = 0; i < TAB_COUNT; i++) {
            if (!tabs[i].inside(panel)) problems.add("tab " + i + " outside panel");
            for (int j = i + 1; j < TAB_COUNT; j++) if (tabs[i].intersects(tabs[j])) problems.add("tabs " + i + "/" + j + " overlap");
        }
        if (!page.inside(panel)) problems.add("page outside panel");
        if (!slots.inside(panel) || slots.intersects(page)) problems.add("slots box " + slots);
        for (int i = 0; i < AffinityView.MAX_SLOTS; i++) {
            Rect row = new Rect(slots.x() + 2, slotTop + i * slotRow, slots.w() - 4, slotRow - 1);
            if (!row.inside(slots)) problems.add("slot row " + i + " outside slots box");
            if (!removeBox(i).inside(row)) problems.add("slot × " + i + " outside its row");
        }
        Rect infoBox = AffinityLayout.pane(panel, AffinityLayout.RIGHT_BOTTOM);
        if (!info.text().inside(infoBox) || info.text().intersects(info.leftButton())) problems.add("info text " + info.text());
        if (info.lines() < 2) problems.add("info box shows only " + info.lines() + " line");
        if (hint != null && (hint.intersects(panel) || !hint.inside(screen))) problems.add("hint line " + hint);
        Rect status = new Rect(page.x(), linkPage.statusY(), page.w(), font.lineHeight);
        if (!status.inside(page) || status.intersects(linkPage.chips())) problems.add("link status line");
        if (!linkPage.chips().inside(page) || linkPage.chips().intersects(linkPage.list())) problems.add("link chips");
        if (!linkPage.list().inside(page)) problems.add("suggestion list " + linkPage.list());
        for (Rect row : hudRows) if (!row.inside(page)) problems.add("hud row " + row);
        List<AbstractWidget> widgets = new ArrayList<>();
        for (var child : children()) if (child instanceof AbstractWidget widget && widget.visible) widgets.add(widget);
        for (int i = 0; i < widgets.size(); i++) {
            Rect a = rect(widgets.get(i));
            if (!a.inside(panel)) problems.add("button outside panel: " + widgets.get(i).getMessage().getString());
            for (int j = i + 1; j < widgets.size(); j++) {
                if (a.intersects(rect(widgets.get(j)))) {
                    problems.add("buttons overlap: " + widgets.get(i).getMessage().getString() + " / "
                            + widgets.get(j).getMessage().getString());
                }
            }
        }
        return problems;
    }

    private static Rect rect(AbstractWidget widget) {
        return new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    private int[] tooltipSize(List<Component> tooltip) {
        List<FormattedCharSequence> lines = wrapTooltip(tooltip);
        int w = 0;
        for (var line : lines) w = Math.max(w, font.width(line));
        return new int[] {w + 8, lines.size() * 10 + 8, width, height};
    }

    // ------------------------------------------------------------------ snapshot

    private void readSnapshot(String snapshot) {
        var json = JsonParser.parseString(snapshot).getAsJsonObject();
        List<String> newAvailable = new ArrayList<>();
        json.getAsJsonArray("available").forEach(value -> newAvailable.add(value.getAsString()));
        List<String> newSelected = new ArrayList<>();
        json.getAsJsonArray("selected").forEach(value -> newSelected.add(value.getAsString()));
        available = List.copyOf(AffinityView.displayOrder(newAvailable));
        selected = List.copyOf(newSelected);
        // The server's own judgement of what is active; a snapshot without it falls back to
        // the same rule (selected and backed by a conscious party Pokémon).
        Set<String> newActive = new LinkedHashSet<>();
        if (json.has("active")) json.getAsJsonArray("active").forEach(value -> newActive.add(value.getAsString()));
        else selected.stream().filter(available::contains).forEach(newActive::add);
        active = Set.copyOf(newActive);
        List<String> newLink = new ArrayList<>();
        if (json.has("link")) json.getAsJsonArray("link").forEach(value -> newLink.add(value.getAsString()));
        link = List.copyOf(newLink);
        Set<String> newLinks = new LinkedHashSet<>();
        if (json.has("links")) json.getAsJsonArray("links").forEach(value -> newLinks.add(value.getAsString()));
        links = Set.copyOf(newLinks);
        List<ResonanceText.PartyMember> newParty = new ArrayList<>();
        if (json.has("party")) {
            json.getAsJsonArray("party").forEach(value -> {
                var member = value.getAsJsonObject();
                String nickname = member.has("nickname") ? member.get("nickname").getAsString() : "";
                Component name = nickname.isEmpty()
                        ? Component.translatable(member.get("species").getAsString())
                        : Component.literal(nickname);
                List<String> types = new ArrayList<>();
                member.getAsJsonArray("types").forEach(type -> types.add(type.getAsString()));
                newParty.add(new ResonanceText.PartyMember(name, List.copyOf(types)));
            });
        }
        party = List.copyOf(newParty);
        partyTypes = ResonanceText.partyTypes(party);
        hudTypes = List.copyOf(AffinityView.hudTypes(selected, active));
        suggestions = LinkSuggestions.of(selected, active, links);
        summaryLines = ResonanceText.summary(selected, active, party, link, links);
        previewKey = "";
        if (replacementSlot >= selected.size()) replacementSlot = -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        panel = AffinityLayout.panel(width, height);
        header = AffinityLayout.pane(panel, AffinityLayout.HEADER);
        Rect leftPane = AffinityLayout.pane(panel, AffinityLayout.LEFT);
        int labelHeight = font.lineHeight + 3;

        // Tab strip on top of the left pane, then the page below it.
        int tabHeight = font.lineHeight + 5;
        int stripTop = leftPane.y() + 1;
        tabBaseline = stripTop + tabHeight;
        int pageX = leftPane.x() + 2;
        int pageW = leftPane.w() - 4;
        page = new Rect(pageX + 1, tabBaseline + 2, pageW - 2, leftPane.bottom() - tabBaseline - 4);
        layoutTabs(pageX + 2, stripTop, pageW - 4, tabHeight);

        grid = AffinityView.grid(page.w() - SCROLLBAR - 2, page.h(), ROW, MIN_COLUMN, available.size());
        scroll = AffinityView.clampScroll(scroll, grid);
        effectLines = new ArrayList<>();
        for (Component line : ResonanceText.activeEffects(selected, active, party, link, links)) {
            effectLines.addAll(font.split(line, Math.max(20, page.w() - SCROLLBAR - 4)));
        }
        effectRows = Math.max(1, (page.h() - 1) / font.lineHeight);
        effectScroll = Math.max(0, Math.min(effectMaxScroll(), effectScroll));
        linkPage = AffinityLayout.linkPage(page, font.lineHeight, ROW, SUGGESTION_ROW, SCROLLBAR);
        suggestionScroll = Math.max(0, Math.min(suggestionMaxScroll(), suggestionScroll));

        slots = AffinityLayout.pane(panel, AffinityLayout.RIGHT_TOP);
        slotLabel = slots.h() >= labelHeight + 3 * 13;
        slotTop = slots.y() + (slotLabel ? labelHeight : 1);
        slotRow = Math.min(ICON + 3, (slots.bottom() - slotTop - 1) / 3);
        slotIcon = Math.min(ICON, slotRow - 1);

        info = AffinityLayout.infoBox(AffinityLayout.pane(panel, AffinityLayout.RIGHT_BOTTOM), font.lineHeight);
        hint = AffinityLayout.hintLine(width, height, panel, font.lineHeight);
        Rect l = info.leftButton();
        leftButton = addRenderableWidget(Button.builder(leftButtonText(), button -> leftButtonPressed())
                .bounds(l.x(), l.y(), l.w(), l.h()).build());
        leftButton.active = tab != TAB_LINK || !link.isEmpty();
        Rect r = info.rightButton();
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(r.x(), r.y(), r.w(), r.h()).build());
        hudControls.clear();
        hudRows = new Rect[0];
        if (tab == TAB_HUD) layoutHudPage();
    }

    private Component leftButtonText() {
        return Component.translatable(switch (tab) {
            case TAB_LINK -> "ui.super_pallet_towner.link_clear";
            case TAB_HUD -> "ui.super_pallet_towner.back";
            default -> "ui.super_pallet_towner.hud_button";
        });
    }

    private void leftButtonPressed() {
        switch (tab) {
            case TAB_LINK -> {
                if (!link.isEmpty()) PacketDistributor.sendToServer(new AffinityPackets.Change("link_clear", ""));
            }
            case TAB_HUD -> selectTab(previousTab == TAB_HUD ? TAB_CANDIDATES : previousTab);
            default -> selectTab(TAB_HUD);
        }
    }

    /** HUD page: size, anchor and offset rows with small buttons on the right, then reset. */
    private void layoutHudPage() {
        hudRows = AffinityLayout.stack(page, HUD_ROW, 3, 4);
        if (hudRows.length > 0) {
            Rect row = hudRows[0];
            hudButton("scale+", "+", row.right() - 16, row.y(), 16, () -> changeHud(HudLayout.SCALE_STEP, 0, 0, 0));
            hudButton("scale-", "-", row.right() - 34, row.y(), 16, () -> changeHud(-HudLayout.SCALE_STEP, 0, 0, 0));
        }
        if (hudRows.length > 1) {
            Rect row = hudRows[1];
            hudButton("anchor>", "▶", row.right() - 16, row.y(), 16, () -> changeHud(0, 1, 0, 0));
            hudButton("anchor<", "◀", row.right() - 34, row.y(), 16, () -> changeHud(0, -1, 0, 0));
        }
        if (hudRows.length > 2) {
            Rect row = hudRows[2];
            int w = 14;
            hudButton("y+", "↓", row.right() - w, row.y(), w, () -> changeHud(0, 0, 0, 4));
            hudButton("y-", "↑", row.right() - 2 * w - 1, row.y(), w, () -> changeHud(0, 0, 0, -4));
            hudButton("x+", "→", row.right() - 3 * w - 2, row.y(), w, () -> changeHud(0, 0, 4, 0));
            hudButton("x-", "←", row.right() - 4 * w - 3, row.y(), w, () -> changeHud(0, 0, -4, 0));
        }
        if (hudRows.length > 3) {
            Rect row = hudRows[3];
            Component label = Component.translatable("ui.super_pallet_towner.hud_reset");
            int w = Math.min(row.w(), font.width(label) + 12);
            hudButton("reset", label.getString(), row.right() - w, row.y(), w, HudSettings::reset);
        }
    }

    private void hudButton(String name, String label, int x, int y, int w, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, HUD_ROW).build());
        hudControls.put(name, button);
    }

    private static void changeHud(int scaleStep, int anchorStep, int dx, int dy) {
        HudLayout.Anchor anchor = HudSettings.anchor();
        int offsetX = HudSettings.offsetX() + dx;
        int offsetY = HudSettings.offsetY() + dy;
        if (anchorStep != 0) {
            anchor = anchor.next(anchorStep < 0);
            // A new anchor starts without the old fine shift.
            offsetX = 0;
            offsetY = 0;
        }
        HudSettings.set(HudSettings.scale() + scaleStep, anchor, offsetX, offsetY);
    }

    /** Full labels when all tabs fit side by side, otherwise the short ones; widths follow the labels. */
    private void layoutTabs(int x, int y, int width, int height) {
        int gap = 3;
        int pad = 10;
        Component[] full = {
                Component.translatable("ui.super_pallet_towner.tab_candidates", available.size()),
                Component.translatable("ui.super_pallet_towner.tab_effects"),
                Component.translatable("ui.super_pallet_towner.tab_link"),
                Component.translatable("ui.super_pallet_towner.tab_hud")};
        Component[] compact = {
                Component.translatable("ui.super_pallet_towner.tab_candidates_short", available.size()),
                Component.translatable("ui.super_pallet_towner.tab_effects_short"),
                Component.translatable("ui.super_pallet_towner.tab_link_short"),
                Component.translatable("ui.super_pallet_towner.tab_hud_short")};
        int fullWidth = gap * (TAB_COUNT - 1);
        for (Component label : full) fullWidth += font.width(label) + pad;
        shortTabs = fullWidth > width;
        Component[] chosen = shortTabs ? compact : full;
        int[] widths = new int[TAB_COUNT];
        int total = gap * (TAB_COUNT - 1);
        for (int i = 0; i < TAB_COUNT; i++) {
            widths[i] = font.width(chosen[i]) + (shortTabs ? 6 : pad);
            total += widths[i];
        }
        // Still too wide (tiny windows): equal widths, labels cut with "…".
        if (total > width) for (int i = 0; i < TAB_COUNT; i++) widths[i] = (width - gap * (TAB_COUNT - 1)) / TAB_COUNT;
        int tx = x;
        for (int i = 0; i < TAB_COUNT; i++) {
            tabs[i] = new Rect(tx, y, widths[i], height);
            tabLabels[i] = chosen[i];
            tx += widths[i] + gap;
        }
    }

    private int suggestionMaxScroll() {
        return Math.max(0, suggestions.size() - linkPage.listRows());
    }

    // ------------------------------------------------------------------ actions

    private void apply(AffinityUiLogic.Action action) {
        if (action.kind() == AffinityUiLogic.Kind.FULL) {
            flash(Component.translatable("ui.super_pallet_towner.flash_full").withStyle(ChatFormatting.RED));
            return;
        }
        if (!action.sends()) return;
        PacketDistributor.sendToServer(new AffinityPackets.Change(action.packetAction(), action.type()));
        replacementSlot = -1;
        previewKey = "";
    }

    private void flash(Component message) {
        flash = message;
        flashUntilMs = net.minecraft.Util.getMillis() + 3000L;
    }

    private void selectTab(int index) {
        if (index == tab) return;
        previousTab = tab;
        tab = index;
        effectScroll = 0;
        suggestionScroll = 0;
        rebuildWidgets();
    }

    private void applySuggestion(LinkSuggestions.Suggestion suggestion) {
        switch (suggestion.state()) {
            case ACTIVE -> PacketDistributor.sendToServer(new AffinityPackets.Change("link_clear", ""));
            case RESTING -> flash(Component.translatable("ui.super_pallet_towner.flash_resting").withStyle(ChatFormatting.RED));
            case READY -> {
                for (var request : LinkSuggestions.plan(link, suggestion.types())) {
                    PacketDistributor.sendToServer(new AffinityPackets.Change(request.action(), request.type()));
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter, keypad Enter
            onClose();
            return true;
        }
        if (keyCode == 258) { // Tab
            selectTab(AffinityUiLogic.nextTab(tab, TAB_COUNT, hasShiftDown()));
            return true;
        }
        int slot = AffinityUiLogic.digitSlot(keyCode);
        if (slot >= 0 && tab != TAB_HUD) {
            apply(AffinityUiLogic.slotRemove(selected, slot));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (int i = 0; i < TAB_COUNT; i++) {
            if (tabs[i].contains(mouseX, mouseY)) {
                selectTab(i);
                return true;
            }
        }
        if (tab == TAB_LINK) {
            String chip = linkChipAt(mouseX, mouseY);
            if (chip != null) {
                // The server checks again; this only avoids sending requests it would refuse.
                if (link.contains(chip)) {
                    PacketDistributor.sendToServer(new AffinityPackets.Change("link_remove", chip));
                } else if (ResonanceLinks.canAdd(link, chip, selected, active)) {
                    PacketDistributor.sendToServer(new AffinityPackets.Change("link_add", chip));
                } else {
                    flash(Component.translatable(active.contains(chip) ? "ui.super_pallet_towner.link_tip_full"
                            : "ui.super_pallet_towner.link_tip_inactive", AffinityTypeIcons.name(chip))
                            .withStyle(ChatFormatting.RED));
                }
                return true;
            }
            LinkSuggestions.Suggestion suggestion = suggestionAt(mouseX, mouseY);
            if (suggestion != null) {
                applySuggestion(suggestion);
                return true;
            }
        }
        String type = tab == TAB_CANDIDATES ? candidateAt(mouseX, mouseY) : null;
        if (type != null) {
            apply(AffinityUiLogic.candidateClick(selected, replacementSlot, type));
            return true;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && slot < selected.size()) {
            if (removeBox(slot).contains(mouseX, mouseY)) {
                apply(AffinityUiLogic.slotRemove(selected, slot));
            } else {
                replacementSlot = replacementSlot == slot ? -1 : slot;
                previewKey = "";
                if (tab != TAB_CANDIDATES) selectTab(TAB_CANDIDATES);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == TAB_EFFECTS && page.contains(mouseX, mouseY)) {
            effectScroll = Math.max(0, Math.min(effectMaxScroll(), effectScroll - 3 * (int) Math.signum(scrollY)));
            return true;
        }
        if (tab == TAB_LINK && linkPage.list().contains(mouseX, mouseY)) {
            suggestionScroll = Math.max(0, Math.min(suggestionMaxScroll(), suggestionScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (tab == TAB_CANDIDATES && page.contains(mouseX, mouseY) && grid.maxScroll() > 0) {
            scroll = AffinityView.clampScroll(scroll - (int) Math.signum(scrollY), grid);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String candidateAt(double mouseX, double mouseY) {
        if (!page.contains(mouseX, mouseY)) return null;
        int columnWidth = columnWidth();
        int column = (int) (mouseX - page.x()) / columnWidth;
        int row = (int) (mouseY - page.y()) / ROW;
        if (column >= grid.columns() || row >= grid.visibleRows()) return null;
        int index = (row + scroll) * grid.columns() + column;
        return index < available.size() ? available.get(index) : null;
    }

    private int slotAt(double mouseX, double mouseY) {
        if (mouseX < slots.x() || mouseX >= slots.right() || mouseY < slotTop) return -1;
        int slot = (int) (mouseY - slotTop) / slotRow;
        return slot < AffinityView.MAX_SLOTS ? slot : -1;
    }

    /** The × at the right end of a slot row. */
    private Rect removeBox(int slot) {
        int h = slotRow - 1;
        int size = Math.min(11, h - 2);
        int x = slots.x() + 2 + slots.w() - 4 - size - 2;
        int y = slotTop + slot * slotRow + (h - size) / 2;
        return new Rect(x, y, size, size);
    }

    private String linkChipAt(double mouseX, double mouseY) {
        Rect chips = linkPage.chips();
        if (!chips.contains(mouseX, mouseY)) return null;
        int index = (int) (mouseX - chips.x()) / (chips.w() / AffinityView.MAX_SLOTS);
        return index < selected.size() ? selected.get(index) : null;
    }

    private LinkSuggestions.Suggestion suggestionAt(double mouseX, double mouseY) {
        Rect list = linkPage.list();
        if (!list.contains(mouseX, mouseY) || mouseX >= list.right() - SCROLLBAR - 1) return null;
        int row = (int) (mouseY - list.y()) / SUGGESTION_ROW;
        if (row >= linkPage.listRows()) return null;
        int index = row + suggestionScroll;
        return index < suggestions.size() ? suggestions.get(index) : null;
    }

    private int columnWidth() {
        return (page.w() - SCROLLBAR - 2) / grid.columns();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Not super.render: that would draw (and blur) the background a second time.
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL, panel.x(), panel.y(), panel.w(), panel.h(), 0, 0, 512, 256, 512, 256);
        int titleY = header.y() + (header.h() - font.lineHeight) / 2 + 1;
        graphics.drawCenteredString(font, title, header.x() + header.w() / 2, titleY, TEXT);

        List<Component> tooltip = renderTabs(graphics, mouseX, mouseY);
        AffinityUiLogic.Action hoverAction = null;
        List<Component> pageTooltip = null;
        switch (tab) {
            case TAB_EFFECTS -> renderScrollingText(graphics, page, effectLines, effectRows, effectScroll, effectMaxScroll());
            case TAB_LINK -> pageTooltip = renderLink(graphics, mouseX, mouseY);
            case TAB_HUD -> renderHudPage(graphics);
            default -> {
                String hovered = candidateAt(mouseX, mouseY);
                if (hovered != null) hoverAction = AffinityUiLogic.candidateClick(selected, replacementSlot, hovered);
                pageTooltip = renderCandidates(graphics, mouseX, mouseY);
            }
        }
        if (pageTooltip != null) tooltip = pageTooltip;
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && slot < selected.size() && removeBox(slot).contains(mouseX, mouseY)) {
            hoverAction = AffinityUiLogic.slotRemove(selected, slot);
        }
        List<Component> slotTooltip = renderSlots(graphics, mouseX, mouseY);
        if (slotTooltip != null) tooltip = slotTooltip;
        List<Component> infoTooltip = renderInfo(graphics, mouseX, mouseY, hoverAction);
        if (infoTooltip != null) tooltip = infoTooltip;
        renderConfirmation(graphics);
        renderHint(graphics);

        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
        if (tab == TAB_HUD) renderHudPreview(graphics);
        if (tooltip != null) graphics.renderTooltip(font, wrapTooltip(tooltip), mouseX, mouseY);
    }

    private void renderConfirmation(GuiGraphics graphics) {
        long remaining = confirmationUntilMs - net.minecraft.Util.getMillis();
        if (remaining <= 0) return;
        Rect area = confirmationLink ? (tab == TAB_LINK ? linkPage.chips() : null) : slots;
        if (area == null) return;
        int alpha = (int) Math.min(180, remaining * 180 / 650);
        int color = (alpha << 24) | (confirmationLink ? 0xE0A8FF : 0x7EE07E);
        graphics.renderOutline(area.x() - 1, area.y() - 1, area.w() + 2, area.h() + 2, color);
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), ((alpha / 4) << 24) | 0xFFFFFF);
    }

    /**
     * Folder tabs over a framed page. The chosen tab is raised, filled like the page and open
     * at the bottom (one shape with the page), with an amber cap and amber text. Inactive tabs are
     * lower and darker behind the page edge; the hovered one lightens, gets a bright edge and text.
     * Returns the full label as a tooltip when a short or cut label is hovered.
     */
    private List<Component> renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int pageLeft = page.x() - 1;
        int pageRight = page.right() + 1;
        int pageBottom = page.bottom() + 1;
        graphics.fill(pageLeft, tabBaseline, pageRight, pageBottom, PAGE);
        graphics.renderOutline(pageLeft - 1, tabBaseline, pageRight - pageLeft + 2, pageBottom - tabBaseline + 1, PAGE_EDGE);
        List<Component> tooltip = null;
        for (int i = 0; i < TAB_COUNT; i++) {
            if (i == tab) continue;
            Rect t = tabs[i];
            boolean hovered = t.contains(mouseX, mouseY);
            int y = t.y() + TAB_DROP;
            int edge = hovered ? TAB_HOVER_EDGE : TAB_IDLE_EDGE;
            graphics.fill(t.x(), y, t.right(), tabBaseline, hovered ? TAB_HOVER : TAB_IDLE);
            graphics.fill(t.x(), y, t.right(), y + 1, edge);
            graphics.fill(t.x(), y, t.x() + 1, tabBaseline, edge);
            graphics.fill(t.right() - 1, y, t.right(), tabBaseline, edge);
            drawTabLabel(graphics, i, y + (tabBaseline - y - font.lineHeight) / 2 + 1, hovered ? TEXT : MUTED, false);
            if (hovered && labelShortened(i)) tooltip = List.of(fullTabLabel(i));
        }
        Rect t = tabs[tab];
        graphics.fill(t.x(), t.y(), t.right(), tabBaseline + 1, PAGE);
        graphics.fill(t.x(), t.y(), t.right(), t.y() + 1, PAGE_EDGE);
        graphics.fill(t.x(), t.y(), t.x() + 1, tabBaseline + 1, PAGE_EDGE);
        graphics.fill(t.right() - 1, t.y(), t.right(), tabBaseline + 1, PAGE_EDGE);
        graphics.fill(t.x() + 1, t.y() + 1, t.right() - 1, t.y() + 3, AMBER);
        drawTabLabel(graphics, tab, t.y() + 3 + (tabBaseline - t.y() - 3 - font.lineHeight) / 2 + 1, AMBER, true);
        if (t.contains(mouseX, mouseY) && labelShortened(tab)) tooltip = List.of(fullTabLabel(tab));
        return tooltip;
    }

    private void drawTabLabel(GuiGraphics graphics, int index, int y, int color, boolean shadow) {
        Rect t = tabs[index];
        Component label = tabLabels[index];
        int labelWidth = font.width(label);
        if (labelWidth <= t.w() - 6) {
            graphics.drawString(font, label, t.x() + (t.w() - labelWidth) / 2, y, color, shadow);
        } else {
            drawClipped(graphics, label, t.x() + 3, y, t.w() - 6, color);
        }
    }

    private boolean labelShortened(int index) {
        return shortTabs || font.width(tabLabels[index]) > tabs[index].w() - 6;
    }

    private Component fullTabLabel(int index) {
        return switch (index) {
            case TAB_EFFECTS -> Component.translatable("ui.super_pallet_towner.tab_effects");
            case TAB_LINK -> Component.translatable("ui.super_pallet_towner.tab_link");
            case TAB_HUD -> Component.translatable("ui.super_pallet_towner.tab_hud");
            default -> Component.translatable("ui.super_pallet_towner.tab_candidates", available.size());
        };
    }

    /** Fits a tooltip to the window: wraps wider when a narrow wrap would be taller than the window. */
    private List<FormattedCharSequence> wrapTooltip(List<Component> lines) {
        int maxWidth = Math.max(80, width - 24);
        int wrap = Math.min(maxWidth, 230);
        List<FormattedCharSequence> wrapped = split(lines, wrap);
        while (wrapped.size() * 10 + 8 > height - 8 && wrap < maxWidth) {
            wrap = Math.min(maxWidth, wrap + 40);
            wrapped = split(lines, wrap);
        }
        return wrapped;
    }

    private List<FormattedCharSequence> split(List<Component> lines, int wrap) {
        List<FormattedCharSequence> result = new ArrayList<>();
        for (Component line : lines) result.addAll(font.split(line, wrap));
        return result;
    }

    private void renderScrollingText(GuiGraphics graphics, Rect area, List<FormattedCharSequence> lines, int rows,
                                     int offset, int maxScroll) {
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        for (int i = 0; i < rows && i + offset < lines.size(); i++) {
            graphics.drawString(font, lines.get(i + offset), area.x() + 2, area.y() + 1 + i * font.lineHeight, TEXT, false);
        }
        graphics.disableScissor();
        scrollbar(graphics, area.right() - SCROLLBAR, area.y(), rows * font.lineHeight, rows, lines.size(), offset, maxScroll);
    }

    private void scrollbar(GuiGraphics graphics, int x, int y, int trackH, int visible, int total, int offset, int max) {
        if (max <= 0) return;
        graphics.fill(x, y, x + SCROLLBAR, y + trackH, 0x66000000);
        int thumbH = Math.max(8, trackH * visible / Math.max(1, total));
        int thumbY = y + (trackH - thumbH) * offset / max;
        graphics.fill(x, thumbY, x + SCROLLBAR, thumbY + thumbH, TEXT);
    }

    // ------------------------------------------------------------------ candidates page

    private List<Component> renderCandidates(GuiGraphics graphics, int mouseX, int mouseY) {
        if (available.isEmpty()) {
            drawWrapped(graphics, Component.translatable("ui.super_pallet_towner.no_candidates"),
                    page.x() + 2, page.y() + 2, page.w() - 4, page.h() - 4, MUTED);
            return null;
        }
        List<Component> tooltip = null;
        int columnWidth = columnWidth();
        boolean full = selected.size() >= AffinityView.MAX_SLOTS && replacementSlot < 0;
        graphics.enableScissor(page.x(), page.y(), page.right(), page.bottom());
        for (int row = 0; row < grid.visibleRows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                int index = (row + scroll) * grid.columns() + column;
                if (index >= available.size()) break;
                String type = available.get(index);
                int x = page.x() + column * columnWidth;
                int y = page.y() + row * ROW;
                int w = columnWidth - 2;
                int slot = selected.indexOf(type);
                boolean blocked = full && slot < 0;
                boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW - 1;
                int background = slot >= 0 ? (hovered ? 0x7730A040 : 0x5530A040)
                        : blocked ? 0x22000000 : hovered ? 0x55FFFFFF : 0x33000000;
                graphics.fill(x, y, x + w, y + ROW - 1, background);
                if (hovered) {
                    int outline = slot >= 0 ? RED_TEXT : blocked ? 0xFF808080 : replacementSlot >= 0 ? AMBER : TEXT;
                    graphics.renderOutline(x, y, w, ROW - 1, outline);
                }
                if (blocked) graphics.setColor(0.55F, 0.55F, 0.55F, 1.0F);
                AffinityTypeIcons.draw(graphics, type, x + 1, y, ICON);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                // Right side: the slot number when chosen, and how many party Pokémon have the type.
                String tag = slot >= 0 ? (hovered ? "×" : "[" + (slot + 1) + "]") : "";
                int count = providerCount(type);
                String countText = count > 0
                        ? Component.translatable("ui.super_pallet_towner.provider_count", count).getString() : "";
                int tagWidth = tag.isEmpty() ? 0 : font.width(tag) + 3;
                int countWidth = countText.isEmpty() ? 0 : font.width(countText) + 3;
                int nameColor = blocked || !AffinityView.isStandard(type) ? MUTED : TEXT;
                int nameMax = w - ICON - 5 - tagWidth - countWidth;
                if (nameMax < 16) {
                    countWidth = 0;
                    nameMax = w - ICON - 5 - tagWidth;
                }
                drawClipped(graphics, AffinityTypeIcons.name(type), x + ICON + 4, y + TEXT_DY, nameMax, nameColor);
                if (countWidth > 0) {
                    graphics.drawString(font, countText, x + w - tagWidth - countWidth, y + TEXT_DY, CYAN, false);
                }
                if (!tag.isEmpty()) {
                    graphics.drawString(font, tag, x + w - tagWidth + 1, y + TEXT_DY, hovered ? RED_TEXT : GREEN, false);
                }
                if (hovered) tooltip = candidateTooltip(type, hasShiftDown());
            }
        }
        graphics.disableScissor();
        scrollbar(graphics, page.right() - SCROLLBAR, page.y(), grid.visibleRows() * ROW, grid.visibleRows(),
                grid.totalRows(), scroll, grid.maxScroll());
        return tooltip;
    }

    private List<Component> candidateTooltip(String type, boolean full) {
        Component action = actionText(AffinityUiLogic.candidateClick(selected, replacementSlot, type));
        Component providers = ResonanceText.providers(type, party);
        return full ? ResonanceText.fullTooltip(type, action, providers)
                // The action and party count are already visible in the info pane and row.
                // Keep the ordinary hover small enough to leave the slots readable.
                : ResonanceText.compactTooltip(type, null, null);
    }

    /** What a click would do, as one coloured line. */
    private Component actionText(AffinityUiLogic.Action action) {
        Component name = AffinityTypeIcons.name(action.type());
        return switch (action.kind()) {
            case SELECT -> Component.translatable("ui.super_pallet_towner.tip_add", name).withStyle(ChatFormatting.GREEN);
            case REPLACE -> Component.translatable("ui.super_pallet_towner.tip_replace", name, action.slot() + 1)
                    .withStyle(ChatFormatting.GOLD);
            case REMOVE -> Component.translatable("ui.super_pallet_towner.tip_remove", name, action.slot() + 1)
                    .withStyle(ChatFormatting.RED);
            case FULL -> Component.translatable("ui.super_pallet_towner.tip_full").withStyle(ChatFormatting.RED);
            case NONE -> Component.empty();
        };
    }

    // ------------------------------------------------------------------ slots and info box

    private List<Component> renderSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        if (slotLabel) {
            long activeCount = selected.stream().filter(active::contains).count();
            drawClipped(graphics, Component.translatable("ui.super_pallet_towner.slots", activeCount,
                    AffinityView.MAX_SLOTS), slots.x() + 3, slots.y() + 2, slots.w() - 6, AMBER);
        }
        List<Component> tooltip = null;
        for (int i = 0; i < AffinityView.MAX_SLOTS; i++) {
            int x = slots.x() + 2;
            int y = slotTop + i * slotRow;
            int w = slots.w() - 4;
            int h = slotRow - 1;
            AffinityView.SlotState state = AffinityView.slotState(selected, active, i);
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int background = switch (state) {
                case ACTIVE -> 0x5530A040;
                case INACTIVE -> 0x55802020;
                case EMPTY -> 0x33000000;
            };
            graphics.fill(x, y, x + w, y + h, background);
            if (i == replacementSlot) graphics.renderOutline(x, y, w, h, AMBER);
            else if (hovered && state != AffinityView.SlotState.EMPTY) graphics.renderOutline(x, y, w, h, TEXT);
            int textY = y + (h - font.lineHeight) / 2 + 1;
            graphics.drawString(font, String.valueOf(i + 1), x + 2, textY, MUTED, false);
            int iconY = y + (h - slotIcon) / 2;
            if (state == AffinityView.SlotState.EMPTY) {
                drawClipped(graphics, Component.translatable("ui.super_pallet_towner.slot_empty_hint"), x + 10, textY,
                        w - 12, MUTED);
                continue;
            }
            String type = selected.get(i);
            boolean on = state == AffinityView.SlotState.ACTIVE;
            if (!on) graphics.setColor(0.45F, 0.45F, 0.45F, 1.0F);
            float pop = i == popSlot && HudSettings.animations()
                    ? HudAnimation.popScale(net.minecraft.Util.getMillis() - popSince) : 1F;
            if (pop != 1F) {
                float ix = x + 10 + slotIcon / 2F;
                float iy = iconY + slotIcon / 2F;
                graphics.pose().pushPose();
                graphics.pose().translate(ix, iy, 0F);
                graphics.pose().scale(pop, pop, 1F);
                graphics.pose().translate(-ix, -iy, 0F);
            }
            AffinityTypeIcons.draw(graphics, type, x + 10, iconY, slotIcon);
            if (pop != 1F) graphics.pose().popPose();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            Rect remove = removeBox(i);
            boolean removeHovered = remove.contains(mouseX, mouseY);
            graphics.fill(remove.x(), remove.y(), remove.right(), remove.bottom(), removeHovered ? 0xCCB03030 : 0x66000000);
            graphics.renderOutline(remove.x(), remove.y(), remove.w(), remove.h(), removeHovered ? 0xFFFFC0C0 : 0xFF8090A8);
            graphics.drawString(font, "×", remove.x() + (remove.w() - font.width("×")) / 2 + 1,
                    remove.y() + (remove.h() - 8) / 2, removeHovered ? 0xFFFFFFFF : RED_TEXT, false);
            Component status = Component.translatable(on ? "ui.super_pallet_towner.active"
                    : "ui.super_pallet_towner.inactive");
            int statusWidth = font.width(status);
            int nameX = x + 10 + slotIcon + 3;
            int room = remove.x() - 3 - nameX;
            boolean showStatus = room - statusWidth - 4 >= 24;
            if (showStatus) {
                graphics.drawString(font, status, remove.x() - 3 - statusWidth, textY, on ? GREEN : RED_TEXT, false);
            }
            drawClipped(graphics, AffinityTypeIcons.name(type), nameX, textY, showStatus ? room - statusWidth - 4 : room,
                    on ? TEXT : MUTED);
            if (removeHovered) {
                tooltip = List.of(Component.translatable("ui.super_pallet_towner.slot_remove_tip", i + 1, i + 1)
                        .withStyle(ChatFormatting.RED));
            } else if (hovered) {
                Component name = AffinityTypeIcons.name(type);
                Component line = on
                        ? Component.translatable("ui.super_pallet_towner.tip_slot_active", name).withStyle(ChatFormatting.GRAY)
                        : Component.translatable("ui.super_pallet_towner.tip_slot_inactive", name).withStyle(ChatFormatting.RED);
                tooltip = hasShiftDown() ? ResonanceText.fullTooltip(type, line, ResonanceText.providers(type, party))
                        : ResonanceText.compactTooltip(type, line, ResonanceText.providers(type, party));
            }
        }
        return tooltip;
    }

    /**
     * The info box: a short message after a refused click, else the preview of the hovered click,
     * else the live summary. Lines that do not fit are cut; hovering the box shows them all.
     */
    private List<Component> renderInfo(GuiGraphics graphics, int mouseX, int mouseY, AffinityUiLogic.Action hover) {
        List<Component> lines = new ArrayList<>();
        if (flashUntilMs > net.minecraft.Util.getMillis()) lines.add(flash);
        if (hover != null && hover.kind() != AffinityUiLogic.Kind.NONE) {
            lines.addAll(previewFor(hover));
        } else {
            if (replacementSlot >= 0) {
                lines.add(Component.translatable("ui.super_pallet_towner.hint_replace_slot", replacementSlot + 1)
                        .withStyle(ChatFormatting.GOLD));
            }
            lines.addAll(summaryLines);
        }
        shownInfo = lines;
        Rect text = info.text();
        // Wrap long lines (e.g. the "slots are full" message) instead of cutting them; only the
        // last visible row is cut, with a marker, when more text follows (hover shows it all).
        List<FormattedCharSequence> rows = new ArrayList<>();
        for (Component line : lines) rows.addAll(font.split(line, Math.max(1, text.w())));
        int y = text.y();
        for (int drawn = 0; drawn < rows.size() && drawn < info.lines(); drawn++) {
            boolean last = drawn == info.lines() - 1 && rows.size() > info.lines();
            if (last) {
                int maxWidth = text.w() - font.width(" ▼");
                drawClippedSequence(graphics, rows.get(drawn), text.x(), y, maxWidth);
                graphics.drawString(font, "▼", text.right() - font.width("▼"), y, MUTED, false);
            } else {
                graphics.drawString(font, rows.get(drawn), text.x(), y, TEXT, false);
            }
            y += font.lineHeight;
        }
        if (text.contains(mouseX, mouseY) && hover == null) return lines;
        return null;
    }

    private List<Component> previewFor(AffinityUiLogic.Action action) {
        String key = action.kind() + ":" + action.slot() + ":" + action.type() + ":" + replacementSlot;
        if (key.equals(previewKey)) return previewLines;
        List<Component> lines;
        Component name = AffinityTypeIcons.name(action.type());
        if (action.kind() == AffinityUiLogic.Kind.FULL) {
            lines = List.of(Component.translatable("ui.super_pallet_towner.flash_full").withStyle(ChatFormatting.RED));
        } else {
            Component head = switch (action.kind()) {
                case SELECT -> Component.translatable("ui.super_pallet_towner.preview_add", name).withStyle(ChatFormatting.GREEN);
                case REMOVE -> Component.translatable("ui.super_pallet_towner.preview_remove", name).withStyle(ChatFormatting.RED);
                default -> Component.translatable("ui.super_pallet_towner.preview_replace", name, action.slot() + 1)
                        .withStyle(ChatFormatting.GOLD);
            };
            Set<String> after = AffinityUiLogic.activeAfter(action, selected, active, available);
            String leaving = action.kind() == AffinityUiLogic.Kind.REMOVE ? action.type()
                    : action.kind() == AffinityUiLogic.Kind.REPLACE ? selected.get(action.slot()) : null;
            Set<String> linksAfter = leaving != null && link.contains(leaving) ? Set.of() : links;
            lines = ResonanceText.preview(head, active, after, links, linksAfter, party);
        }
        previewKey = key;
        previewLines = lines;
        return lines;
    }

    private Component hintComponent() {
        return Component.translatable(switch (tab) {
            case TAB_LINK -> "ui.super_pallet_towner.hint_keys_link";
            case TAB_HUD -> "ui.super_pallet_towner.hint_keys_hud";
            default -> "ui.super_pallet_towner.hint_keys";
        });
    }

    private void renderHint(GuiGraphics graphics) {
        if (hint == null) return;
        Component text = hintComponent();
        int textWidth = Math.min(font.width(text), hint.w());
        drawClipped(graphics, text, hint.x() + (hint.w() - textWidth) / 2, hint.y() + 1, hint.w(), MUTED);
    }

    // ------------------------------------------------------------------ link page

    private ResonanceLinks.Status linkState() {
        ResonanceLinks.Status status = ResonanceLinks.status(link, active);
        ResonanceLinks.Recipe recipe = ResonanceLinks.recipe(link);
        // The server's verdict wins: a recipe is shown as working only when the server says so.
        if (status == ResonanceLinks.Status.ACTIVE && (recipe == null || !links.contains(recipe.id()))) {
            return ResonanceLinks.Status.RESTING;
        }
        return status;
    }

    private static Component summaryOf(String id) {
        ResonanceInfo.Line line = ResonanceInfo.linkSummary(id);
        return line == null ? Component.literal(id) : ResonanceText.line(line);
    }

    /** One line: what the current link does, or why it does nothing. */
    private Component linkStatusLine() {
        ResonanceLinks.Recipe recipe = ResonanceLinks.recipe(link);
        return switch (linkState()) {
            case EMPTY -> Component.translatable("ui.super_pallet_towner.link_status_empty").withStyle(ChatFormatting.GRAY);
            case TOO_FEW -> Component.translatable("ui.super_pallet_towner.link_status_too_few").withStyle(ChatFormatting.YELLOW);
            case NO_RECIPE -> Component.translatable("ui.super_pallet_towner.link_status_no_recipe",
                    ResonanceText.typeList(link)).withStyle(ChatFormatting.YELLOW);
            case RESTING -> Component.translatable("ui.super_pallet_towner.link_status_resting",
                    ResonanceText.typeList(link)).withStyle(ChatFormatting.RED);
            case ACTIVE -> Component.translatable("ui.super_pallet_towner.link_status_active", ResonanceText.typeList(link))
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" ─ ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(summaryOf(recipe.id()).copy().withStyle(ChatFormatting.WHITE));
        };
    }

    private List<Component> renderLink(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> tooltip = null;
        // Status line.
        Component status = linkStatusLine();
        drawClipped(graphics, status, page.x() + 2, linkPage.statusY(), page.w() - 4, TEXT);
        if (mouseY >= linkPage.statusY() && mouseY < linkPage.statusY() + font.lineHeight
                && mouseX >= page.x() && mouseX < page.right()) {
            ResonanceLinks.Recipe recipe = ResonanceLinks.recipe(link);
            tooltip = recipe == null ? List.of(status) : ResonanceText.linkTooltip(recipe, status);
        }
        // Chips: the resonance-slot types; a click links or unlinks one (manual link).
        Rect chips = linkPage.chips();
        int chipWidth = chips.w() / AffinityView.MAX_SLOTS;
        for (int i = 0; i < AffinityView.MAX_SLOTS; i++) {
            int x = chips.x() + i * chipWidth;
            int y = chips.y();
            int w = chipWidth - 2;
            int h = chips.h() - 1;
            if (i >= selected.size()) {
                graphics.fill(x, y, x + w, y + h, 0x22000000);
                continue;
            }
            String type = selected.get(i);
            boolean linked = link.contains(type);
            boolean on = active.contains(type);
            boolean addable = ResonanceLinks.canAdd(link, type, selected, active);
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int background = linked ? (hovered ? 0x8830A040 : 0x5530A040) : addable ? (hovered ? 0x66FFFFFF : 0x44000000) : 0x22000000;
            graphics.fill(x, y, x + w, y + h, background);
            graphics.renderOutline(x, y, w, h, hovered ? (linked ? RED_TEXT : addable ? TEXT : 0xFF808080)
                    : linked ? PURPLE : 0xFF4A5670);
            if (!on) graphics.setColor(0.45F, 0.45F, 0.45F, 1.0F);
            drawTypeCell(graphics, type, x, y, w, linked ? "✔" : "", addable || linked ? TEXT : MUTED);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (hovered) {
                String key = linked ? "ui.super_pallet_towner.link_tip_remove_type"
                        : !on ? "ui.super_pallet_towner.link_tip_inactive"
                        : link.size() >= ResonanceLinks.MAX_LINK ? "ui.super_pallet_towner.link_tip_full"
                        : "ui.super_pallet_towner.link_tip_add";
                tooltip = List.of(AffinityTypeIcons.name(type).copy().withStyle(ChatFormatting.BOLD),
                        Component.translatable(key, AffinityTypeIcons.name(type)).withStyle(ChatFormatting.GRAY));
            }
        }
        if (linkPage.captionY() >= 0) {
            drawClipped(graphics, Component.translatable("ui.super_pallet_towner.link_caption"), page.x() + 2,
                    linkPage.captionY(), page.w() - 4, AMBER);
        }
        Rect list = linkPage.list();
        graphics.fill(list.x() + 1, list.y() - 1, list.right() - 1, list.y(), 0x55F8F0DE);
        if (suggestions.isEmpty()) {
            drawWrapped(graphics, Component.translatable("ui.super_pallet_towner.link_none_possible"),
                    list.x() + 2, list.y() + 2, list.w() - 4, list.h() - 2, MUTED);
            return tooltip;
        }
        int rowWidth = list.w() - SCROLLBAR - 2;
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int row = 0; row < linkPage.listRows(); row++) {
            int index = row + suggestionScroll;
            if (index >= suggestions.size()) break;
            LinkSuggestions.Suggestion s = suggestions.get(index);
            int x = list.x() + 1;
            int y = list.y() + row * SUGGESTION_ROW;
            boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + SUGGESTION_ROW - 1;
            if (renderSuggestion(graphics, s, x, y, rowWidth, hovered)) tooltip = suggestionTooltip(s);
        }
        graphics.disableScissor();
        scrollbar(graphics, list.right() - SCROLLBAR, list.y(), linkPage.listRows() * SUGGESTION_ROW,
                linkPage.listRows(), suggestions.size(), suggestionScroll, suggestionMaxScroll());
        return tooltip;
    }

    /** One suggestion row: badge, type icons, one-line effect, state. Returns whether it is hovered. */
    private boolean renderSuggestion(GuiGraphics graphics, LinkSuggestions.Suggestion s, int x, int y, int w,
                                     boolean hovered) {
        int h = SUGGESTION_ROW - 1;
        int background = switch (s.state()) {
            case ACTIVE -> 0x6630A040;
            case RESTING -> 0x33000000;
            case READY -> hovered ? 0x55FFFFFF : 0x44000000;
        };
        graphics.fill(x, y, x + w, y + h, background);
        if (hovered) graphics.renderOutline(x, y, w, h, s.state() == LinkSuggestions.State.RESTING ? 0xFF808080
                : s.state() == LinkSuggestions.State.ACTIVE ? RED_TEXT : TEXT);
        else if (s.state() == LinkSuggestions.State.ACTIVE) {
            // The active link breathes gently instead of a static outline.
            float breath = HudSettings.animations() ? HudAnimation.starBreath(net.minecraft.Util.getMillis()) : 1F;
            graphics.renderOutline(x, y, w, h, HudAnimation.argb(140 + Math.round(115 * breath), GREEN & 0xFFFFFF));
        }
        int textY = y + (h - 8) / 2;
        // Badge: gold "special" for Signature, steel blue "standard".
        Component badge = Component.translatable(s.signature() ? "ui.super_pallet_towner.badge_signature"
                : "ui.super_pallet_towner.badge_standard");
        int badgeW = font.width(badge) + 4;
        graphics.fill(x + 1, y + 1, x + 1 + badgeW, y + h - 1, s.signature() ? 0xFFB8862E : 0xFF4A6488);
        if (s.signature() && HudSettings.animations()) {
            // A narrow light sweeps across the gold badge now and then (first third of each cycle).
            long cycle = Math.floorMod(net.minecraft.Util.getMillis() + y * 37L, 2400L);
            if (cycle < 800L) {
                int sweep = x + 1 + Math.round((badgeW + 6) * (cycle / 800F)) - 3;
                int left = Math.max(x + 1, sweep);
                int right = Math.min(x + 1 + badgeW, sweep + 3);
                if (right > left) graphics.fill(left, y + 1, right, y + h - 1, 0x66FFF4C8);
            }
        }
        graphics.drawString(font, badge, x + 3, textY, s.signature() ? 0xFF1A1206 : 0xFFE6EEFF, false);
        int cx = x + 3 + badgeW;
        if (s.state() == LinkSuggestions.State.RESTING) graphics.setColor(0.5F, 0.5F, 0.5F, 1.0F);
        for (String type : s.types()) {
            AffinityTypeIcons.draw(graphics, type, cx, y + (h - SUGGESTION_ICON) / 2, SUGGESTION_ICON);
            cx += SUGGESTION_ICON + 1;
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        Component state = switch (s.state()) {
            case ACTIVE -> Component.translatable("ui.super_pallet_towner.suggest_active");
            case RESTING -> Component.translatable("ui.super_pallet_towner.suggest_resting");
            case READY -> Component.empty();
        };
        int stateW = s.state() == LinkSuggestions.State.READY ? 0 : font.width(state) + 3;
        if (stateW > 0) {
            graphics.drawString(font, state, x + w - stateW, textY,
                    s.state() == LinkSuggestions.State.ACTIVE ? GREEN : RED_TEXT, false);
        }
        drawClipped(graphics, summaryOf(s.id()), cx + 2, textY, x + w - stateW - cx - 4,
                s.state() == LinkSuggestions.State.RESTING ? MUTED : TEXT);
        return hovered;
    }

    private List<Component> suggestionTooltip(LinkSuggestions.Suggestion s) {
        String key = switch (s.state()) {
            case ACTIVE -> "ui.super_pallet_towner.suggest_tip_clear";
            case RESTING -> "ui.super_pallet_towner.suggest_tip_resting";
            case READY -> "ui.super_pallet_towner.suggest_tip_apply";
        };
        ResonanceLinks.Recipe recipe = new ResonanceLinks.Recipe(s.id(), Set.copyOf(s.types()));
        List<Component> lines = ResonanceText.linkTooltip(recipe, Component.translatable(s.signature()
                ? "ui.super_pallet_towner.badge_signature_long" : "ui.super_pallet_towner.badge_standard_long"));
        lines.add(1, Component.translatable(key).withStyle(s.state() == LinkSuggestions.State.RESTING
                ? ChatFormatting.RED : ChatFormatting.GREEN));
        return lines;
    }

    /**
     * A type symbol with its name and an optional green mark at the right. When the name does not
     * fit whole (narrow windows) only the symbol is drawn, centred; the tooltip names the type.
     */
    private void drawTypeCell(GuiGraphics graphics, String type, int x, int y, int w, String mark, int color) {
        int markWidth = mark.isEmpty() ? 0 : font.width(mark) + 2;
        Component name = AffinityTypeIcons.name(type);
        if (ICON + 3 + font.width(name) + markWidth + 2 <= w) {
            AffinityTypeIcons.draw(graphics, type, x + 1, y, ICON);
            graphics.drawString(font, name, x + ICON + 3, y + TEXT_DY, color, false);
        } else {
            AffinityTypeIcons.draw(graphics, type, x + (w - markWidth - ICON) / 2, y, ICON);
        }
        if (!mark.isEmpty()) graphics.drawString(font, mark, x + w - markWidth, y + TEXT_DY, GREEN, false);
    }

    // ------------------------------------------------------------------ HUD page

    private void renderHudPage(GuiGraphics graphics) {
        String[] keys = {"ui.super_pallet_towner.hud_scale", "ui.super_pallet_towner.hud_anchor",
                "ui.super_pallet_towner.hud_offset"};
        for (int i = 0; i < Math.min(3, hudRows.length); i++) {
            Rect row = hudRows[i];
            Component label = switch (i) {
                case 0 -> Component.translatable(keys[0], HudSettings.scale());
                case 1 -> Component.translatable(keys[1], Component.translatable(
                        "ui.super_pallet_towner.hud_anchor." + HudSettings.anchor().id));
                default -> Component.translatable(keys[2], signed(HudSettings.offsetX()), signed(HudSettings.offsetY()));
            };
            int buttonsLeft = row.right();
            for (Button button : hudControls.values()) {
                if (button.getY() == row.y()) buttonsLeft = Math.min(buttonsLeft, button.getX());
            }
            drawClipped(graphics, label, row.x(), row.y() + (row.h() - 8) / 2, buttonsLeft - row.x() - 3, TEXT);
        }
        int noteY = hudRows.length == 0 ? page.y() + 2 : hudRows[hudRows.length - 1].bottom() + 3;
        if (noteY < page.bottom()) {
            Component note = Component.translatable(hudTypes.isEmpty() ? "ui.super_pallet_towner.hud_note_sample"
                    : "ui.super_pallet_towner.hud_note");
            drawWrapped(graphics, note, page.x() + 2, noteY, page.w() - 4, page.bottom() - noteY, MUTED);
        }
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    /** The HUD drawn where it will appear in game, with a pulsing outline. */
    private void renderHudPreview(GuiGraphics graphics) {
        boolean sample = hudTypes.isEmpty();
        graphics.pose().pushPose();
        graphics.pose().translate(0F, 0F, 300F);
        AffinityHud.draw(graphics, sample ? SAMPLE_TYPES : hudTypes, sample ? NO_LINKS : links, width, height);
        int[] b = AffinityHud.lastBounds();
        float pulse = TypeIconHighlight.pulse(net.minecraft.Util.getMillis());
        int alpha = 120 + (int) (pulse * 135);
        graphics.renderOutline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, (alpha << 24) | 0xFFD45A);
        graphics.pose().popPose();
    }

    // ------------------------------------------------------------------ text helpers

    /** Draws a wrapped row, cutting it with "…" if it is wider than maxWidth. */
    private void drawClippedSequence(GuiGraphics graphics, FormattedCharSequence row, int x, int y, int maxWidth) {
        if (font.width(row) <= maxWidth) {
            graphics.drawString(font, row, x, y, TEXT, false);
            return;
        }
        StringBuilder plain = new StringBuilder();
        row.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
        drawClipped(graphics, Component.literal(plain.toString()), x, y, maxWidth, TEXT);
    }

    private void drawClipped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width("…")));
        graphics.drawString(font, FormattedCharSequence.composite(
                net.minecraft.locale.Language.getInstance().getVisualOrder(cut),
                FormattedCharSequence.forward("…", net.minecraft.network.chat.Style.EMPTY)), x, y, color, false);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int maxHeight, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, maxWidth));
        int maxLines = Math.max(1, maxHeight / font.lineHeight);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * font.lineHeight, color, false);
        }
    }
}
