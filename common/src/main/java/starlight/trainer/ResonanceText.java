package starlight.trainer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Client-side text for the type-resonance screen, built from {@link ResonanceInfo}: the
 * per-type tooltip and the "active effects" list. Only display; the server decides what is
 * active and sends it in the snapshot.
 */
final class ResonanceText {
    /** A conscious party Pokémon from the server snapshot. */
    record PartyMember(Component name, List<String> types) {}

    private ResonanceText() {}

    static Component line(ResonanceInfo.Line line) {
        return Component.translatable(line.translationKey(), line.args().toArray());
    }

    private static Component bullet(ResonanceInfo.Line line, ChatFormatting colour) {
        return Component.literal("・").append(line(line)).withStyle(colour);
    }

    private static Component header(String key, ChatFormatting colour) {
        return Component.translatable(key).withStyle(colour, ChatFormatting.BOLD);
    }

    static Component typeList(Collection<String> types) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (String type : types) {
            if (!first) result.append("・");
            result.append(AffinityTypeIcons.name(type));
            first = false;
        }
        return result;
    }

    /**
     * Everything a type does when it resonates. {@code action} is the click hint (add, replace,
     * …) shown under the name, or {@code null}.
     */
    static List<Component> tooltip(String type, Component action) {
        List<Component> lines = new ArrayList<>();
        lines.add(AffinityTypeIcons.name(type).copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        if (action != null) lines.add(action.copy().withStyle(ChatFormatting.GRAY));
        ResonanceInfo.TypeInfo info = ResonanceInfo.of(type);
        if (info == null) {
            lines.add(Component.translatable("ui.super_pallet_towner.undefined_type").withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("ui.super_pallet_towner.undefined_detail").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(header("ui.super_pallet_towner.section_player", ChatFormatting.GOLD));
        for (var line : info.player()) lines.add(bullet(line, ChatFormatting.WHITE));
        lines.add(header("ui.super_pallet_towner.section_pokemon", ChatFormatting.AQUA));
        for (var line : info.pokemon()) lines.add(bullet(line, ChatFormatting.WHITE));
        lines.add(header("ui.super_pallet_towner.section_notes", ChatFormatting.GRAY));
        lines.add(bullet(ResonanceInfo.Line.of("scope"), ChatFormatting.GRAY));
        for (var line : info.notes()) lines.add(bullet(line, ChatFormatting.GRAY));
        return lines;
    }

    /**
     * The short hover text of a type: name, what a click does, which party Pokémon have it and
     * the trainer-side effects. The full text ({@link #tooltip}) is shown while Shift is held.
     */
    static List<Component> compactTooltip(String type, Component action, Component providers) {
        List<Component> lines = new ArrayList<>();
        lines.add(AffinityTypeIcons.name(type).copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        if (action != null) lines.add(action);
        if (providers != null) lines.add(providers);
        ResonanceInfo.TypeInfo info = ResonanceInfo.of(type);
        if (info == null) {
            lines.add(Component.translatable("ui.super_pallet_towner.undefined_type").withStyle(ChatFormatting.RED));
            return lines;
        }
        lines.add(header("ui.super_pallet_towner.section_player", ChatFormatting.GOLD));
        for (var line : info.player()) lines.add(bullet(line, ChatFormatting.WHITE));
        lines.add(Component.translatable("ui.super_pallet_towner.shift_more").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** {@link #tooltip} with the party line under the action line. */
    static List<Component> fullTooltip(String type, Component action, Component providers) {
        List<Component> lines = tooltip(type, null);
        int at = 1;
        if (action != null) lines.add(at++, action);
        if (providers != null) lines.add(at, providers);
        return lines;
    }

    /** "In party: A, B" for the Pokémon that have this type, or {@code null} when none. */
    static Component providers(String type, List<PartyMember> party) {
        MutableComponent names = Component.empty();
        int count = 0;
        for (PartyMember member : party) {
            if (!member.types().contains(type)) continue;
            if (count++ > 0) names.append("、");
            names.append(member.name());
        }
        if (count == 0) return null;
        return Component.translatable("ui.super_pallet_towner.providers", names).withStyle(ChatFormatting.AQUA);
    }

    /** Compact live summary for the info box: counts, link, trainer totals, Pokémon count. */
    static List<Component> summary(List<String> selected, Set<String> active, List<PartyMember> party,
                                   List<String> link, Set<String> links) {
        List<Component> lines = new ArrayList<>();
        List<String> activeInOrder = AffinityView.hudTypes(selected, active);
        ResonanceLinks.Recipe recipe = ResonanceLinks.recipe(link);
        Component linkPart = recipe != null && links.contains(recipe.id())
                ? Component.translatable("ui.super_pallet_towner.summary_link", typeList(link))
                : Component.translatable("ui.super_pallet_towner.summary_no_link");
        lines.add(Component.translatable("ui.super_pallet_towner.summary_head", activeInOrder.size(),
                AffinityView.MAX_SLOTS, linkPart).withStyle(ChatFormatting.YELLOW));
        if (activeInOrder.isEmpty()) {
            lines.add(Component.translatable("ui.super_pallet_towner.summary_none").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        Set<String> activeSet = Set.copyOf(activeInOrder);
        for (var line : ResonanceInfo.playerTotals(activeSet, links)) lines.add(bullet(line, ChatFormatting.WHITE));
        for (String type : activeInOrder) {
            for (var line : ResonanceInfo.playerSpecials(type)) {
                lines.add(Component.literal("・").append(AffinityTypeIcons.name(type)).append(": ")
                        .append(line(line)).withStyle(ChatFormatting.WHITE));
            }
        }
        lines.add(Component.translatable("ui.super_pallet_towner.summary_pokemon",
                AffinityUiLogic.membersWithActiveType(partyTypes(party), activeSet)).withStyle(ChatFormatting.AQUA));
        return lines;
    }

    /**
     * What changes if a click goes through: trainer totals and type specials that appear (+) or
     * disappear (−), and how many party Pokémon get a return buff. Display only.
     */
    static List<Component> preview(Component head, Set<String> before, Set<String> after, Set<String> linksBefore,
                                   Set<String> linksAfter, List<PartyMember> party) {
        List<Component> lines = new ArrayList<>();
        lines.add(head);
        List<ResonanceInfo.Line> totalsBefore = ResonanceInfo.playerTotals(before, linksBefore);
        List<ResonanceInfo.Line> totalsAfter = ResonanceInfo.playerTotals(after, linksAfter);
        int changes = 0;
        for (var line : totalsAfter) {
            if (!totalsBefore.contains(line)) {
                lines.add(Component.literal("+ ").append(line(line)).withStyle(ChatFormatting.GREEN));
                changes++;
            }
        }
        for (String type : after) {
            if (before.contains(type)) continue;
            for (var line : ResonanceInfo.playerSpecials(type)) {
                lines.add(Component.literal("+ ").append(AffinityTypeIcons.name(type)).append(": ").append(line(line))
                        .withStyle(ChatFormatting.GREEN));
                changes++;
            }
        }
        for (var line : totalsBefore) {
            if (!totalsAfter.contains(line)) {
                lines.add(Component.literal("− ").append(line(line)).withStyle(ChatFormatting.RED));
                changes++;
            }
        }
        for (String type : before) {
            if (after.contains(type)) continue;
            for (var line : ResonanceInfo.playerSpecials(type)) {
                lines.add(Component.literal("− ").append(AffinityTypeIcons.name(type)).append(": ").append(line(line))
                        .withStyle(ChatFormatting.RED));
                changes++;
            }
        }
        if (!linksBefore.isEmpty() && linksAfter.isEmpty()) {
            lines.add(Component.translatable("ui.super_pallet_towner.preview_link_off").withStyle(ChatFormatting.RED));
            changes++;
        }
        if (changes == 0) {
            lines.add(Component.translatable("ui.super_pallet_towner.preview_same").withStyle(ChatFormatting.GRAY));
        }
        var types = partyTypes(party);
        int countBefore = AffinityUiLogic.membersWithActiveType(types, before);
        int countAfter = AffinityUiLogic.membersWithActiveType(types, after);
        if (countBefore != countAfter) {
            lines.add(Component.translatable("ui.super_pallet_towner.preview_pokemon", countBefore, countAfter)
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    static List<List<String>> partyTypes(List<PartyMember> party) {
        List<List<String>> result = new ArrayList<>(party.size());
        for (PartyMember member : party) result.add(member.types());
        return result;
    }

    /**
     * The effects that apply now: active types only (selected and backed by a conscious party
     * Pokémon, as decided by the server), trainer totals and specials, then each party Pokémon
     * that shares an active type with its own per-Pokémon totals after the caps.
     */
    static List<Component> activeEffects(List<String> selected, Set<String> active, List<PartyMember> party) {
        return activeEffects(selected, active, party, List.of(), Set.of());
    }

    /** As above, plus the resonance link: {@code links} are the recipe ids the server says apply now. */
    static List<Component> activeEffects(List<String> selected, Set<String> active, List<PartyMember> party,
                                         List<String> link, Set<String> links) {
        List<Component> lines = activeEffectsWithoutLink(selected, active, party, links);
        ResonanceLinks.Recipe recipe = ResonanceLinks.recipe(link);
        if (recipe == null) return lines;
        if (links.contains(recipe.id())) {
            lines.add(header("ui.super_pallet_towner.effects_links", ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.literal("・").append(typeList(link)).append(": ")
                    .append(line(ResonanceInfo.linkSummary(recipe.id()))).withStyle(ChatFormatting.WHITE));
        } else {
            lines.add(Component.translatable("ui.super_pallet_towner.effects_link_resting", typeList(link))
                    .withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /** Tooltip of a link recipe: name, effect, details. {@code state} is the line under the name, or null. */
    static List<Component> linkTooltip(ResonanceLinks.Recipe recipe, Component state) {
        List<Component> lines = new ArrayList<>();
        lines.add(typeList(List.copyOf(recipe.types().stream().sorted(
                java.util.Comparator.comparingInt(AffinityView.STANDARD_TYPES::indexOf)).toList()))
                .copy().withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (state != null) lines.add(state.copy().withStyle(ChatFormatting.GRAY));
        lines.add(line(ResonanceInfo.linkSummary(recipe.id())).copy().withStyle(ChatFormatting.GOLD));
        for (var detail : ResonanceInfo.linkDetails(recipe.id())) lines.add(bullet(detail, ChatFormatting.WHITE));
        return lines;
    }

    private static List<Component> activeEffectsWithoutLink(List<String> selected, Set<String> active,
                                                            List<PartyMember> party, Set<String> links) {
        List<Component> lines = new ArrayList<>();
        List<String> activeInOrder = AffinityView.hudTypes(selected, active);
        List<String> resting = selected.stream().filter(type -> !activeInOrder.contains(type)).toList();
        if (activeInOrder.isEmpty()) {
            lines.add(Component.translatable("ui.super_pallet_towner.effects_none").withStyle(ChatFormatting.GRAY));
            if (!resting.isEmpty()) {
                lines.add(Component.translatable("ui.super_pallet_towner.effects_resting", typeList(resting))
                        .withStyle(ChatFormatting.RED));
            }
            return lines;
        }
        lines.add(Component.translatable("ui.super_pallet_towner.effects_types", typeList(activeInOrder))
                .withStyle(ChatFormatting.GREEN));
        if (!resting.isEmpty()) {
            lines.add(Component.translatable("ui.super_pallet_towner.effects_resting", typeList(resting))
                    .withStyle(ChatFormatting.RED));
        }
        Set<String> activeSet = Set.copyOf(activeInOrder);
        lines.add(header("ui.super_pallet_towner.effects_player", ChatFormatting.GOLD));
        for (var line : ResonanceInfo.playerTotals(activeSet, links)) lines.add(bullet(line, ChatFormatting.WHITE));
        for (String type : activeInOrder) {
            List<ResonanceInfo.Line> specials = ResonanceInfo.playerSpecials(type);
            if (ResonanceInfo.of(type) == null) {
                lines.add(Component.literal("・").append(AffinityTypeIcons.name(type)).append(": ")
                        .append(Component.translatable("ui.super_pallet_towner.undefined_type"))
                        .withStyle(ChatFormatting.GRAY));
            }
            for (var line : specials) {
                lines.add(Component.literal("・").append(AffinityTypeIcons.name(type)).append(": ")
                        .append(line(line)).withStyle(ChatFormatting.WHITE));
            }
        }
        lines.add(header("ui.super_pallet_towner.effects_pokemon", ChatFormatting.AQUA));
        boolean any = false;
        for (PartyMember member : party) {
            List<ResonanceInfo.Line> effects = ResonanceInfo.pokemonNow(activeSet, member.types(), links);
            if (effects.isEmpty()) continue;
            any = true;
            lines.add(Component.translatable("ui.super_pallet_towner.species_types", member.name(),
                    typeList(member.types())).withStyle(ChatFormatting.YELLOW));
            for (var line : effects) lines.add(bullet(line, ChatFormatting.WHITE));
        }
        if (!any) {
            lines.add(Component.translatable("ui.super_pallet_towner.effects_pokemon_none").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
