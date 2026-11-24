package com.onlyk.justepicportals;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** An opening in one of the three block planes. Corners are deliberately never inspected. */
public record PortalFrame(BlockPos origin, Direction normal, int width, int height) {
    public Direction u() { return normal.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST; }
    public Direction v() { return normal.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP; }
    public BlockPos position(int x, int y) { return origin.relative(u(), x).relative(v(), y); }
    public Vec3 point(double x, double y, double depth) {
        return Vec3.atCenterOf(origin).add(Vec3.atLowerCornerOf(u().getNormal()).scale(x - .5))
                .add(Vec3.atLowerCornerOf(v().getNormal()).scale(y - .5))
                .add(Vec3.atLowerCornerOf(normal.getNormal()).scale(depth));
    }
    public boolean contains(BlockPos pos) {
        BlockPos delta = pos.subtract(origin);
        int x = project(delta, u()), y = project(delta, v());
        return project(delta, normal) == 0 && x >= 0 && x < width && y >= 0 && y < height;
    }
    public void forEach(Consumer<BlockPos> consumer) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) consumer.accept(position(x, y));
    }
    public void forEachEdge(Consumer<BlockPos> consumer) {
        for (int x = 0; x < width; x++) { consumer.accept(position(x, -1)); consumer.accept(position(x, height)); }
        for (int y = 0; y < height; y++) { consumer.accept(position(-1, y)); consumer.accept(position(width, y)); }
    }
    private static int project(BlockPos pos, Direction direction) {
        return pos.getX() * direction.getStepX() + pos.getY() * direction.getStepY() + pos.getZ() * direction.getStepZ();
    }

    /** Why an anchor has no usable frame, from least to most specific. Each has a short message for either end of a link. */
    public enum Problem {
        /** The opening's air escapes through a gap, meets another block, or spans more than 9 blocks. */
        INCOMPLETE("incomplete"),
        /** The anchor is in a corner of the frame, or touches no opening at all. */
        CORNER("corner"),
        SHAPE("shape"),
        TOO_SMALL("too_small"),
        ANCHORS("anchors"),
        /** The frame is valid but another anchor's portal already fills part of it. */
        OVERLAP("overlap"),
        /** The anchor borders more than one valid opening and the clicked or recorded side picks neither. */
        AMBIGUOUS("ambiguous");
        private final String id;
        Problem(String id) { this.id = id; }
        public String message(boolean destination) { return (destination ? "destination_frame." : "frame.") + id; }
    }

    public static Optional<PortalFrame> find(Level level, BlockPos anchor, PortalMaterial material, Direction normal) {
        return search(level, anchor, material, normal, null);
    }

    /** The most specific reason none of the anchor's three planes holds a valid frame, or null if one does. */
    public static Problem diagnose(Level level, BlockPos anchor, PortalMaterial material) {
        Problem[] worst = {null};
        for (Direction normal : new Direction[]{Direction.EAST, Direction.UP, Direction.SOUTH}) {
            if (search(level, anchor, material, normal, problem -> {
                if (worst[0] == null || problem.ordinal() > worst[0].ordinal()) worst[0] = problem;
            }).isPresent()) return null;
            if (corner(level, anchor, material, normal) && (worst[0] == null || worst[0] == Problem.INCOMPLETE)) worst[0] = Problem.CORNER;
        }
        return worst[0] == null ? Problem.CORNER : worst[0];
    }

    /** True if, in this plane, the anchor sits where two frame sides meet around the corner of an opening. */
    private static boolean corner(Level level, BlockPos anchor, PortalMaterial material, Direction normal) {
        var axes = new PortalFrame(anchor, normal, 0, 0);
        for (Direction across : new Direction[]{axes.u(), axes.u().getOpposite()}) {
            for (Direction along : new Direction[]{axes.v(), axes.v().getOpposite()}) {
                if (interior(level.getBlockState(anchor.relative(across).relative(along)))
                        && frameMaterial(level.getBlockState(anchor.relative(across)), material)
                        && frameMaterial(level.getBlockState(anchor.relative(along)), material)) return true;
            }
        }
        return false;
    }

    private static Optional<PortalFrame> search(Level level, BlockPos anchor, PortalMaterial material, Direction normal, Consumer<Problem> problems) {
        Consumer<Problem> report = problems == null ? problem -> {} : problems;
        var axes = new PortalFrame(anchor, normal, 0, 0);
        for (Direction edge : new Direction[]{axes.u(), axes.u().getOpposite(), axes.v(), axes.v().getOpposite()}) {
            BlockPos seed = anchor.relative(edge);
            if (!interior(level.getBlockState(seed))) continue;
            var queue = new ArrayDeque<BlockPos>();
            var visited = new HashSet<BlockPos>();
            queue.add(seed);
            int minU = 0, maxU = 0, minV = 0, maxV = 0;
            boolean invalid = false;
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                if (!visited.add(pos)) continue;
                int x = project(pos.subtract(seed), axes.u()), y = project(pos.subtract(seed), axes.v());
                minU = Math.min(minU, x); maxU = Math.max(maxU, x);
                minV = Math.min(minV, y); maxV = Math.max(maxV, y);
                if (visited.size() > 81 || maxU - minU >= 9 || maxV - minV >= 9) { invalid = true; break; }
                for (Direction direction : new Direction[]{axes.u(), axes.u().getOpposite(), axes.v(), axes.v().getOpposite()}) {
                    BlockPos next = pos.relative(direction);
                    BlockState state = level.getBlockState(next);
                    if (interior(state)) queue.add(next);
                    else if (!frameMaterial(state, material)) { invalid = true; break; }
                }
                if (invalid) break;
            }
            if (invalid) { report.accept(Problem.INCOMPLETE); continue; }
            int width = maxU - minU + 1, height = maxV - minV + 1;
            if (visited.size() != width * height) { report.accept(Problem.SHAPE); continue; }
            if (width * height < 2 || (normal.getAxis() != Direction.Axis.Y && height < 2)) { report.accept(Problem.TOO_SMALL); continue; }
            var result = new PortalFrame(seed.relative(axes.u(), minU).relative(axes.v(), minV), normal, width, height);
            int anchors = 0;
            boolean valid = true;
            for (int x = 0; x < width; x++) {
                for (int y : new int[]{-1, height}) {
                    var state = level.getBlockState(result.position(x, y));
                    valid &= frameMaterial(state, material);
                    if (state.getBlock() instanceof PortalAnchorBlock) anchors++;
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x : new int[]{-1, width}) {
                    var state = level.getBlockState(result.position(x, y));
                    valid &= frameMaterial(state, material);
                    if (state.getBlock() instanceof PortalAnchorBlock) anchors++;
                }
            }
            if (valid && anchors == 1) return Optional.of(result);
            report.accept(valid ? Problem.ANCHORS : Problem.INCOMPLETE);
        }
        return Optional.empty();
    }

    private static boolean interior(BlockState state) {
        return state.isAir() || state.getBlock() instanceof PortalSurfaceBlock;
    }
    private static boolean frameMaterial(BlockState state, PortalMaterial material) {
        return state.is(material.frameBlock()) || (state.getBlock() instanceof PortalAnchorBlock block && block.material() == material);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("origin", origin.asLong()); tag.putInt("normal", normal.get3DDataValue());
        tag.putInt("width", width); tag.putInt("height", height);
        return tag;
    }
    public static Optional<PortalFrame> load(CompoundTag tag) {
        int width = tag.getInt("width"), height = tag.getInt("height");
        if (width < 1 || width > 9 || height < 1 || height > 9 || width * height < 2) return Optional.empty();
        return Optional.of(new PortalFrame(BlockPos.of(tag.getLong("origin")), Direction.from3DDataValue(tag.getInt("normal")), width, height));
    }
}
