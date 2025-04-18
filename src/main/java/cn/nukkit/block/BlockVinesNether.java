package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.OptionalBoolean;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implements the main logic of all nether vines.
 * @author joserobjr
 */
public abstract class BlockVinesNether extends BlockTransparent {
    /**
     * Creates a nether vine from a meta compatible with {@link #getProperties()}.
     */
    public BlockVinesNether(BlockState blockstate) {
        super(blockstate);
    }

    /**
     * The direction that the vine will grow, vertical direction is expected but future implementations
     * may also add horizontal directions.
     * @return Normally, up or down.
     */
    @NotNull public abstract BlockFace getGrowthDirection();

    /**
     * The current age of this block.
     */
    public abstract int getVineAge();

    public abstract void setVineAge(int vineAge);

    /**
     * The maximum accepted age of this block.
     * @return Positive, inclusive value.
     */
    public abstract int getMaxVineAge();

    /**
     * Changes the current vine age to a random new random age. 
     * 
     * @param pseudorandom If the the randomization should be pseudorandom.
     */
    public void randomizeVineAge(boolean pseudorandom) {
        if (pseudorandom) {
            setVineAge(ThreadLocalRandom.current().nextInt(getMaxVineAge()));
            return;
        }
        
        double chance = 1.0D;
        int age;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for(age = 0; random.nextDouble() < chance; ++age) {
            chance *= 0.826D;
        }
        
        setVineAge(age);
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        Block support = getSide(getGrowthDirection().getOpposite());
        if (!isSupportValid(support)) {
            return false;
        }

        if (support.getId().equals(getId())) {
            setVineAge(Math.min(getMaxVineAge(), ((BlockVinesNether) support).getVineAge() + 1));
        } else {
            randomizeVineAge(true);
        }
        
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    @Override
    public int onUpdate(int type) {
        switch (type) {
            case Level.BLOCK_UPDATE_RANDOM -> {
                int maxVineAge = getMaxVineAge();
                if (getVineAge() < maxVineAge && ThreadLocalRandom.current().nextInt(10) == 0
                        && findVineAge(true).orElse(maxVineAge) < maxVineAge) {
                    grow();
                }
                return Level.BLOCK_UPDATE_RANDOM;
            }
            case Level.BLOCK_UPDATE_SCHEDULED -> {
                getLevel().useBreakOn(this, null, null, true);
                return Level.BLOCK_UPDATE_SCHEDULED;
            }
            case Level.BLOCK_UPDATE_NORMAL -> {
                if (!isSupportValid()) {
                    getLevel().scheduleUpdate(this, 1);
                }
                return Level.BLOCK_UPDATE_NORMAL;
            }
            default -> {
                return 0;
            }
        }
    }

    /**
     * Grow a single vine if possible. Calls {@link BlockGrowEvent} passing the positioned new state and the source block.
     * @return If the vine grew successfully.
     */
    public boolean grow() {
        Block pos = getSide(getGrowthDirection());
        if (!pos.isAir() || pos.y < 0 || 255 < pos.y) {
            return false;
        }

        BlockVinesNether growing = clone();
        growing.x = pos.x;
        growing.y = pos.y;
        growing.z = pos.z;
        growing.setVineAge(Math.min(getVineAge() + 1, getMaxVineAge()));

        BlockGrowEvent ev = new BlockGrowEvent(this, growing);
        Server.getInstance().getPluginManager().callEvent(ev);

        if (ev.isCancelled()) {
            return false;
        }
        
        if (level.setBlock(pos, growing)) {
            increaseRootAge();
            return true;
        }
        return false;
    }

    /**
     * Grow a random amount of vines. 
     * Calls {@link BlockGrowEvent} passing the positioned new state and the source block for each new vine being added
     * to the world, if one of the events gets cancelled the growth gets interrupted.
     * @return How many vines grew 
     */
    public int growMultiple() {
        BlockFace growthDirection = getGrowthDirection();
        int age = getVineAge() + 1;
        int maxAge = getMaxVineAge();
        BlockVinesNether growing = clone();
        growing.randomizeVineAge(false);
        int blocksToGrow = growing.getVineAge();

        int grew = 0;
        for (int distance = 1; distance <= blocksToGrow; distance++) {
            Block pos = getSide(growthDirection, distance);
            if (!pos.isAir() || pos.y < 0 || 255 < pos.y) {
                break;
            }

            growing.setVineAge(Math.min(age++, maxAge));
            growing.x = pos.x;
            growing.y = pos.y;
            growing.z = pos.z;

            BlockGrowEvent ev = new BlockGrowEvent(this, growing.clone());
            Server.getInstance().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                break;
            }

            if (!level.setBlock(pos, ev.getNewState())) {
                break;
            }

            grew++;
        }
        
        if (grew > 0) {
            increaseRootAge();
        }

        return grew;
    }

    /**
     * Attempt to get the age of the root or the head of the vine.
     * @param base True to get the age of the base (oldest block), false to get the age of the head (newest block)
     * @return Empty if the target could not be reached. The age of the target if it was found.
     */
    @NotNull public OptionalInt findVineAge(boolean base) {
        return findVineBlock(base)
                .map(vine-> OptionalInt.of(vine.getVineAge()))
                .orElse(OptionalInt.empty());
    }

    /**
     * Attempt to find the root or the head of the vine transversing the growth direction for up to 256 blocks.
     * @param base True to find the base (oldest block), false to find the head (newest block)
     * @return Empty if the target could not be reached or the block there isn't an instance of {@link BlockVinesNether}.
     *          The positioned block of the target if it was found.
     */
    @NotNull public Optional<BlockVinesNether> findVineBlock(boolean base) {
        return findVine(base)
                .map(Position::getLevelBlock)
                .filter(BlockVinesNether.class::isInstance)
                .map(BlockVinesNether.class::cast);
    }

    /**
     * Attempt to find the root or the head of the vine transversing the growth direction for up to 256 blocks.
     * @param base True to find the base (oldest block), false to find the head (newest block)
     * @return Empty if the target could not be reached. The position of the target if it was found.
     */
    @NotNull public Optional<Position> findVine(boolean base) {
        BlockFace supportFace = getGrowthDirection();
        if (base) {
            supportFace = supportFace.getOpposite();
        }
        Position result = getLocation();
        String id = getId();
        int limit = 256;
        while (--limit > 0){
            Position next = result.getSide(supportFace);
            if (Objects.equals(next.getLevelBlockState().getIdentifier(), id)) {
                result = next;
            } else {
                break;
            }
        }
        
        return limit == -1 ? Optional.empty() : Optional.of(result);
    }

    /**
     * Attempts to increase the age of the base of the nether vine.
     * @return <ul>
     *     <li>{@code EMPTY} if the base could not be reached or have an invalid instance type
     *     <li>{@code TRUE} if the base was changed successfully
     *     <li>{@code FALSE} if the base was already in the max age or the block change was refused 
     *     </ul>
     */
    @NotNull public OptionalBoolean increaseRootAge() {
        Block base = findVine(true).map(Position::getLevelBlock).orElse(null);
        if (!(base instanceof BlockVinesNether baseVine)) {
            return OptionalBoolean.EMPTY;
        }

        int vineAge = baseVine.getVineAge();
        if (vineAge < baseVine.getMaxVineAge()) {
            baseVine.setVineAge(vineAge + 1);
            if (getLevel().setBlock(baseVine, baseVine)) {
                return OptionalBoolean.TRUE;
            }
        }
        
        return OptionalBoolean.FALSE;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player, BlockFace blockFace, float fx, float fy, float fz) {
        if (!item.isFertilizer()) {
            return false;
        }

        getLevel().addParticle(new BoneMealParticle(this));
        findVineBlock(false).ifPresent(BlockVinesNether::growMultiple);

        if (player != null && !player.isCreative()) {
            item.count--;
        }
        
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        // They have a 33% (3/9) chance to drop a single weeping vine when broken, 
        // increased to 55% (5/9) with Fortune I, 
        // 77% (7/9) with Fortune II, 
        // and 100% with Fortune III. 
        // 
        // They always drop a single weeping vine when broken with shears or a tool enchanted with Silk Touch.

        int enchantmentLevel;
        if (item.isShears() || (enchantmentLevel = item.getEnchantmentLevel(Enchantment.ID_FORTUNE_DIGGING)) >= 3) {
            return new Item[]{ toItem() };
        }
        
        int chance = 3 + enchantmentLevel * 2;
        if (ThreadLocalRandom.current().nextInt(9) < chance) {
            return new Item[]{ toItem() };
        }
        
        return Item.EMPTY_ARRAY;
    }

    protected boolean isSupportValid(@NotNull Block support) {
        return support.getId().equals(getId()) || !support.isTransparent();
    }

    public boolean isSupportValid() {
        return isSupportValid(getSide(getGrowthDirection().getOpposite()));
    }

    @Override
    public void onEntityCollide(Entity entity) {
        entity.resetFallDistance();
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public double getHardness() {
        return 0;
    }

    @Override
    public double getResistance() {
        return 0;
    }

    @Override
    public boolean canBeClimbed() {
        return true;
    }

    @Override
    public boolean canBeFlowedInto() {
        return true;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public double getMinX() {
        return x+ (4/16.0);
    }

    @Override
    public double getMinZ() {
        return z+ (4/16.0);
    }

    @Override
    public double getMaxX() {
        return x+ (12/16.0);
    }

    @Override
    public double getMaxZ() {
        return z+ (12/16.0);
    }

    @Override
    public double getMaxY() {
        return y+ (15/16.0);
    }

    @Override
    public boolean canPassThrough() {
        return true;
    }

    @Override
    public  boolean sticksToPiston() {
        return false;
    }

    @Override
    public boolean breaksWhenMoved() {
        return true;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }
    @Override
    public BlockVinesNether clone() {
        return (BlockVinesNether) super.clone();
    }

    @Override
    public boolean isFertilizable() {
        return true;
    }
}
