package mce.encoder.create;

import com.simibubi.create.content.kinetics.crafter.CrafterHelper;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Geometry + loading for a connected wall of Create mechanical crafters.
 *
 * <p>Used by the mixins that make Create's package unpacker lay a smaller recipe pattern onto a
 * wall that is bigger than the recipe (e.g. a 3x4 or 5x6 recipe on a shared 9x9 wall).</p>
 *
 * <p>Instead of guessing a physical orientation, every crafter's coordinate is derived from the
 * {@code POINTING} arrows exactly the way Create builds its own craft grid ({@code mergeOnto}):
 * an item ends up at the summed arrow deltas along its path to the controller. We place recipe
 * row 0 at logical maxY / col 0 at logical minX, which is Create's own fold convention, so the
 * orientation always matches no matter how the player arranged the wall.</p>
 */
public final class CrafterWall {
    private static final Logger LOG = LoggerFactory.getLogger("mce_encoder");

    private final int rows;
    private final int cols;
    private final Map<Long, MechanicalCrafterBlockEntity> cell;

    private CrafterWall(int rows, int cols, Map<Long, MechanicalCrafterBlockEntity> cell) {
        this.rows = rows;
        this.cols = cols;
        this.cell = cell;
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    /** Crafter at recipe coordinates (row from the top, col from the left), or null if absent. */
    @Nullable
    public MechanicalCrafterBlockEntity cellAt(int r, int c) {
        if (r < 0 || c < 0 || r >= rows || c >= cols) return null;
        return cell.get(key(r, c));
    }

    public Iterable<MechanicalCrafterBlockEntity> all() {
        return cell.values();
    }

    private static long key(int r, int c) {
        return ((long) r << 32) | (c & 0xffffffffL);
    }

    /** Discover the whole connected crafter wall that touches {@code anchorPos}. */
    @Nullable
    public static CrafterWall discover(Level level, BlockPos anchorPos) {
        MechanicalCrafterBlockEntity anchor = CrafterHelper.getCrafter(level, anchorPos);
        if (anchor == null) return null;

        // BFS over the same-connected component.
        Set<BlockPos> seen = new HashSet<>();
        java.util.Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> positions = new ArrayList<>();
        seen.add(anchorPos);
        queue.add(anchorPos);
        positions.add(anchorPos);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.remove();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.relative(d);
                if (!seen.contains(np) && CrafterHelper.getCrafter(level, np) != null
                        && areSameGrid(level, anchorPos, np)) {
                    seen.add(np);
                    queue.add(np);
                    positions.add(np);
                }
            }
        }
        if (positions.isEmpty()) return null;

        Set<MechanicalCrafterBlockEntity> crafters = new HashSet<>();
        for (BlockPos p : positions) {
            MechanicalCrafterBlockEntity be = CrafterHelper.getCrafter(level, p);
            if (be != null) crafters.add(be);
        }
        if (crafters.isEmpty()) return null;

        // Logical coordinates from the POINTING arrows (Create's own fold space).
        Map<MechanicalCrafterBlockEntity, int[]> coord = new HashMap<>();
        // Terminal crafters (nothing downstream) sit at (0,0).
        for (MechanicalCrafterBlockEntity c : crafters) {
            MechanicalCrafterBlockEntity t = targetIn(c, crafters);
            if (t == null) coord.put(c, new int[] {0, 0});
        }
        if (coord.isEmpty()) return null;

        for (MechanicalCrafterBlockEntity c : crafters) {
            logicalCoord(c, crafters, coord, new HashSet<>());
        }

        List<Integer> lxs = new ArrayList<>();
        List<Integer> lys = new ArrayList<>();
        for (int[] v : coord.values()) {
            if (!lxs.contains(v[0])) lxs.add(v[0]);
            if (!lys.contains(v[1])) lys.add(v[1]);
        }
        lxs.sort(Integer::compareTo);
        lys.sort(Integer::compareTo);

        Map<Long, MechanicalCrafterBlockEntity> cell = new HashMap<>();
        for (MechanicalCrafterBlockEntity c : crafters) {
            int[] v = coord.get(c);
            int colFromLeft = lxs.indexOf(v[0]);
            int rowFromTop = lys.size() - 1 - lys.indexOf(v[1]);
            cell.put(key(rowFromTop, colFromLeft), c);
        }
        return new CrafterWall(lys.size(), lxs.size(), cell);
    }

    /** The crafter {@code c} points at, provided it belongs to {@code crafters}. */
    @Nullable
    private static MechanicalCrafterBlockEntity targetIn(MechanicalCrafterBlockEntity c, Set<MechanicalCrafterBlockEntity> crafters) {
        try {
            MechanicalCrafterBlockEntity t = RecipeGridHandler.getTargetingCrafter(c);
            return (t != null && crafters.contains(t)) ? t : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int[] logicalCoord(MechanicalCrafterBlockEntity c, Set<MechanicalCrafterBlockEntity> crafters,
                                      Map<MechanicalCrafterBlockEntity, int[]> cache, Set<MechanicalCrafterBlockEntity> visiting) {
        int[] got = cache.get(c);
        if (got != null) return got;
        if (!visiting.add(c)) { // cycle guard, should never happen in a real layout
            int[] zero = {0, 0};
            cache.put(c, zero);
            return zero;
        }
        MechanicalCrafterBlockEntity t = targetIn(c, crafters);
        int[] base;
        if (t == null) {
            base = new int[] {0, 0};
        } else {
            base = logicalCoord(t, crafters, cache, visiting);
        }
        int[] d = pointingDelta(c);
        int[] out = new int[] {base[0] + d[0], base[1] + d[1]};
        visiting.remove(c);
        cache.put(c, out);
        return out;
    }

    /** {dx,dy} in Create's merge space: LEFT=+x, RIGHT=-x, DOWN=+y, UP=-y. */
    private static int[] pointingDelta(MechanicalCrafterBlockEntity c) {
        BlockState bs = c.getBlockState();
        for (Property<?> p : bs.getProperties()) {
            if (!p.getName().equals("pointing")) continue;
            Object val = bs.getValue(p);
            String s = val == null ? "" : val.toString();
            return switch (s) {
                case "LEFT" -> new int[] {1, 0};
                case "RIGHT" -> new int[] {-1, 0};
                case "DOWN" -> new int[] {0, 1};
                case "UP" -> new int[] {0, -1};
                default -> new int[] {0, 0};
            };
        }
        return new int[] {0, 0};
    }

    /**
     * Load the w*h recipe pattern (already in row-major order) onto the top-left corner of the wall.
     * Materials are taken from {@code src}. During a real (non-simulated) load the whole chain is put
     * into "loading" (suppressing Create's auto-start) and the anchor is armed for a deferred trigger
     * once the wall spins. Returns true only when everything was placed.
     */
    public static boolean load(Level level, MechanicalCrafterBlockEntity anchor, CrafterWall wall,
                               int w, int h, List<BigItemStack> pattern, ItemStackHandler src, boolean simulate) {
        if (pattern.size() != w * h) return false;
        if (wall == null) return false;

        // ---- plan: never touch real inventories during planning ----
        int[] remaining = new int[src.getSlots()];
        for (int s = 0; s < remaining.length; s++) remaining[s] = src.getStackInSlot(s).getCount();

        List<MechanicalCrafterBlockEntity> targets = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();

        for (int i = 0; i < pattern.size(); i++) {
            BigItemStack cellB = pattern.get(i);
            if (cellB == null || cellB.stack.isEmpty()) continue;
            int r = i / w;
            int c = i % w;
            MechanicalCrafterBlockEntity t = wall.cellAt(r, c);
            if (t == null) return false;
            if (!t.getInventory().getStackInSlot(0).isEmpty()) return false; // occupied already
            // choose a source slot for this material
            int srcSlot = -1;
            for (int s = 0; s < remaining.length; s++) {
                if (remaining[s] > 0 && sameItem(src.getStackInSlot(s), cellB.stack)) {
                    srcSlot = s;
                    break;
                }
            }
            if (srcSlot < 0) return false;
            remaining[srcSlot]--;
            // must be accepted by the crafter right now (rejects if it is mid-assembly)
            if (!t.getInventory().insertItem(0, cellB.stack.copyWithCount(1), true).isEmpty()) return false;
            targets.add(t);
            slots.add(srcSlot);
        }
        if (targets.isEmpty()) return false;
        if (simulate) return true;

        // ---- commit ----
        setLoading(wall, true);
        int committed = 0;
        try {
            for (int k = 0; k < targets.size(); k++) {
                ItemStack one = src.extractItem(slots.get(k), 1, false);
                if (one.isEmpty()) {
                    rollBack(targets, slots, src, committed);
                    return false;
                }
                ItemStack left = targets.get(k).getInventory().insertItem(0, one, false);
                if (!left.isEmpty()) {
                    src.insertItem(slots.get(k), left, false);
                    rollBack(targets, slots, src, committed);
                    return false;
                }
                committed++;
            }
        } finally {
            setLoading(wall, false);
        }
        if (committed == targets.size()) {
            if (anchor instanceof MceCrafterExt ext) ext.mceSetPendingCraft(true);
            LOG.info("[mce-wall-feed] loaded {} items onto {}x{} wall corner (pattern {}x{}), pending trigger armed",
                    targets.size(), wall.rows(), wall.cols(), w, h);
            return true;
        }
        return false;
    }

    private static void rollBack(List<MechanicalCrafterBlockEntity> targets, List<Integer> slots,
                                 ItemStackHandler src, int upTo) {
        for (int k = 0; k < upTo; k++) {
            MechanicalCrafterBlockEntity t = targets.get(k);
            ItemStack taken = t.getInventory().getStackInSlot(0);
            if (!taken.isEmpty()) {
                t.getInventory().setStackInSlot(0, ItemStack.EMPTY);
                src.insertItem(slots.get(k), taken, false);
            }
        }
    }

    private static void setLoading(CrafterWall wall, boolean value) {
        for (MechanicalCrafterBlockEntity c : wall.all()) {
            if (c instanceof MceCrafterExt ext) ext.mceSetLoading(value);
        }
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return !a.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean areSameGrid(Level level, BlockPos a, BlockPos b) {
        try {
            return CrafterHelper.areCraftersConnected(level, a, b);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
