package cn.nukkit.entity.ai.executor;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityIntelligent;
import cn.nukkit.entity.ai.memory.CoreMemoryTypes;
import cn.nukkit.entity.ai.memory.MemoryType;
import cn.nukkit.entity.effect.Effect;
import cn.nukkit.entity.effect.EffectType;
import cn.nukkit.entity.passive.EntityBee;

import static cn.nukkit.Server.getInstance;

public class BeeAttackExecutor extends MeleeAttackExecutor {

    public BeeAttackExecutor(MemoryType<? extends Entity> memory, float speed, int maxSenseRange, boolean clearDataWhenLose, int coolDown) {
        super(memory, speed, maxSenseRange, clearDataWhenLose, coolDown);
    }

    @Override
    public boolean execute(EntityIntelligent entity) {
        if(entity instanceof EntityBee bee) {
            if (entity.getMemoryStorage().notEmpty(CoreMemoryTypes.ATTACK_TARGET)) {
                if (!entity.isEnablePitch()) entity.setEnablePitch(true);
                Entity entity1 = entity.getMemoryStorage().get(CoreMemoryTypes.ATTACK_TARGET);
                if (entity1 != null) {
                    this.lookTarget = entity1.clone();
                    if (getInstance().getDifficulty() == 2) {
                        entity1.addEffect(Effect.get(EffectType.POISON).setDuration(200));
                    } else if (getInstance().getDifficulty() == 3) {
                        entity1.addEffect(Effect.get(EffectType.POISON).setDuration(360));
                    }
                }
            }

            if (entity.distanceSquared(entity.getMemoryStorage().get(CoreMemoryTypes.ATTACK_TARGET)) <= 2.5 && attackTick > coolDown && bee.hasSting()) {
                bee.dieInTicks = 700;
            }
            return super.execute(entity);
        }
        return false;
    }

    @Override
    public void onStop(EntityIntelligent entity) {
        stop(entity);
        super.onStop(entity);
    }

    @Override
    public void onInterrupt(EntityIntelligent entity) {
        stop(entity);
        super.onInterrupt(entity);
    }

    private void stop(EntityIntelligent entity) {
        var bee = (EntityBee) entity;
        entity.getLevel().getScheduler().scheduleDelayedTask(null, () -> bee.setAngry(false), 5);
    }
}
