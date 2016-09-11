/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.event.tracking.phase;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.BlockSpawnCause;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.InternalNamedCauses;
import org.spongepowered.common.event.tracking.CauseTracker;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.MutableWrapper;
import org.spongepowered.common.interfaces.IMixinChunk;
import org.spongepowered.common.interfaces.world.IMixinLocation;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.registry.type.event.InternalSpawnTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class BlockPhase extends TrackingPhase {

    public enum State implements IPhaseState {
        BLOCK_DECAY,
        RESTORING_BLOCKS(false),
        DISPENSE,
        BLOCK_DROP_ITEMS,
        BLOCK_ADDED,
        BLOCK_BREAK,
        PISTON_MOVING,
        ;

        private final boolean allowsSpawns;

        State() {
            this.allowsSpawns = true;
        }

        State(boolean allowsSpawns) {
            this.allowsSpawns = allowsSpawns;
        }

        @Override
        public boolean canSwitchTo(IPhaseState state) {
            return false;
        }

        @Override
        public BlockPhase getPhase() {
            return TrackingPhases.BLOCK;
        }

    }

    BlockPhase(TrackingPhase parent) {
        super(parent);
    }

    @Override
    public boolean requiresBlockCapturing(IPhaseState currentState) {
        return currentState != State.RESTORING_BLOCKS;
    }

    @Override
    public boolean allowEntitySpawns(IPhaseState currentState) {
        return ((State) currentState).allowsSpawns;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void unwind(CauseTracker causeTracker, IPhaseState state, PhaseContext phaseContext) {
        if (state == State.BLOCK_DECAY) {
            final BlockSnapshot blockSnapshot = phaseContext.getSource(BlockSnapshot.class)
                    .orElseThrow(TrackingUtil.throwWithContext("Could not find a decaying block snapshot!", phaseContext));
            final Location<World> worldLocation = blockSnapshot.getLocation().get();
            final BlockPos blockPos = ((IMixinLocation) (Object) worldLocation).getBlockPos();
            final IMixinWorldServer mixinWorld = causeTracker.getMixinWorld();
            final IMixinChunk mixinChunk = (IMixinChunk) causeTracker.getMinecraftWorld().getChunkFromBlockCoords(blockPos);
            final Optional<User> notifier = mixinChunk.getBlockNotifier(blockPos);
            final Optional<User> creator = mixinChunk.getBlockOwner(blockPos);

            phaseContext.getCapturedItemsSupplier()
                    .ifPresentAndNotEmpty(items -> {
                        // Nothing happens here yet for some reason.
                    });
            phaseContext.getCapturedEntitySupplier()
                    .ifPresentAndNotEmpty(entities -> {
                        final Cause.Builder builder = Cause.source(BlockSpawnCause.builder()
                                .block(blockSnapshot)
                                .type(InternalSpawnTypes.BLOCK_SPAWNING)
                                .build());
                        phaseContext.getNotifier()
                                .ifPresent(builder::notifier);
                        phaseContext.getOwner()
                                .ifPresent(builder::owner);

                        final Cause cause = builder
                                .build();
                        final SpawnEntityEvent
                                event =
                                SpongeEventFactory.createSpawnEntityEvent(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            for (Entity entity : event.getEntities()) {
                                causeTracker.getMixinWorld().forceSpawnEntity(entity);
                            }
                        }
                    });
            phaseContext.getCapturedBlockSupplier()
                    .ifPresentAndNotEmpty(blocks -> TrackingUtil.processBlockCaptures(blocks, causeTracker, state, phaseContext));
            phaseContext.getCapturedItemStackSupplier()
                    .ifPresentAndNotEmpty(drops -> {
                        final List<EntityItem> items = drops.stream()
                                .map(drop -> drop.create(causeTracker.getMinecraftWorld()))
                                .collect(Collectors.toList());
                        final Cause.Builder builder = Cause.source(
                                BlockSpawnCause.builder()
                                        .block(blockSnapshot)
                                        .type(InternalSpawnTypes.BLOCK_SPAWNING)
                                        .build()
                        );
                        notifier.ifPresent(user -> builder.named(NamedCause.notifier(user)));
                        creator.ifPresent(user -> builder.named(NamedCause.owner(user)));
                        final Cause cause = builder.build();
                        final List<Entity> entities = (List<Entity>) (List<?>) items;
                        if (!entities.isEmpty()) {
                            DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(cause, entities, causeTracker.getWorld());
                            SpongeImpl.postEvent(event);
                            if (!event.isCancelled()) {
                                for (Entity droppedItem : event.getEntities()) {
                                    mixinWorld.forceSpawnEntity(droppedItem);
                                }
                            }
                        }
                    });
        } else if (state == State.BLOCK_DROP_ITEMS) {
            final BlockSnapshot blockSnapshot = phaseContext.getSource(BlockSnapshot.class)
                    .orElseThrow(TrackingUtil.throwWithContext("Could not find a block dropping items!", phaseContext));
            phaseContext.getCapturedItemsSupplier()
                    .ifPresentAndNotEmpty(items -> {
                        final Cause.Builder builder = Cause.source(BlockSpawnCause.builder()
                                .block(blockSnapshot)
                                .type(InternalSpawnTypes.DROPPED_ITEM)
                                .build());
                        phaseContext.getNotifier()
                                .ifPresent(builder::notifier);
                        phaseContext.getOwner()
                                .ifPresent(builder::owner);

                        final Cause cause = builder
                                .build();
                        final ArrayList<Entity> entities = new ArrayList<>();
                        for (EntityItem item : items) {
                            entities.add(EntityUtil.fromNative(item));
                        }
                        final DropItemEvent.Destruct
                                event =
                                SpongeEventFactory.createDropItemEventDestruct(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            for (Entity entity : event.getEntities()) {
                                causeTracker.getMixinWorld().forceSpawnEntity(entity);
                            }
                        }
                    });
            phaseContext.getCapturedEntitySupplier()
                    .ifPresentAndNotEmpty(entities -> {
                        final Cause.Builder builder = Cause.source(BlockSpawnCause.builder()
                                .block(blockSnapshot)
                                .type(InternalSpawnTypes.DROPPED_ITEM)
                                .build());
                        phaseContext.getNotifier()
                                .ifPresent(builder::notifier);
                        phaseContext.getOwner()
                                .ifPresent(builder::owner);

                        final Cause cause = builder
                                .build();
                        final SpawnEntityEvent
                                event =
                                SpongeEventFactory.createSpawnEntityEvent(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            for (Entity entity : event.getEntities()) {
                                causeTracker.getMixinWorld().forceSpawnEntity(entity);
                            }
                        }
                    });
            final Location<World> worldLocation = blockSnapshot.getLocation().get();
            final BlockPos blockPos = ((IMixinLocation) (Object) worldLocation).getBlockPos();
            final IMixinWorldServer mixinWorld = causeTracker.getMixinWorld();
            final IMixinChunk mixinChunk = (IMixinChunk) causeTracker.getMinecraftWorld().getChunkFromBlockCoords(blockPos);
            final Optional<User> notifier = mixinChunk.getBlockNotifier(blockPos);
            final Optional<User> creator = mixinChunk.getBlockOwner(blockPos);

            phaseContext.getCapturedBlockSupplier()
                    .ifPresentAndNotEmpty(blocks -> TrackingUtil.processBlockCaptures(blocks, causeTracker, state, phaseContext));
            phaseContext.getCapturedItemStackSupplier()
                    .ifPresentAndNotEmpty(drops -> {
                        final List<EntityItem> items = drops.stream()
                                .map(drop -> drop.create(causeTracker.getMinecraftWorld()))
                                .collect(Collectors.toList());
                        final Cause.Builder builder = Cause.source(
                                BlockSpawnCause.builder()
                                        .block(blockSnapshot)
                                        .type(InternalSpawnTypes.BLOCK_SPAWNING)
                                        .build()
                        );
                        notifier.ifPresent(user -> builder.named(NamedCause.notifier(user)));
                        creator.ifPresent(user -> builder.named(NamedCause.owner(user)));
                        final Cause cause = builder.build();
                        final List<Entity> entities = (List<Entity>) (List<?>) items;
                        if (!entities.isEmpty()) {
                            DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(cause, entities, causeTracker.getWorld());
                            SpongeImpl.postEvent(event);
                            if (!event.isCancelled()) {
                                for (Entity droppedItem : event.getEntities()) {
                                    mixinWorld.forceSpawnEntity(droppedItem);
                                }
                            }
                        }
                    });
        } else if (state == State.RESTORING_BLOCKS) {
            // do nothing for now.
        } else if (state == State.DISPENSE) {
            final BlockSnapshot blockSnapshot = phaseContext.getSource(BlockSnapshot.class)
                    .orElseThrow(TrackingUtil.throwWithContext("Could not find a block dispensing items!", phaseContext));
            phaseContext.getCapturedItemsSupplier()
                    .ifPresentAndNotEmpty(items -> {
                        final Cause.Builder builder = Cause.source(BlockSpawnCause.builder()
                                .block(blockSnapshot)
                                .type(InternalSpawnTypes.DISPENSE)
                                .build());
                        phaseContext.getNotifier()
                                .ifPresent(builder::notifier);
                        phaseContext.getOwner()
                                .ifPresent(builder::owner);

                        final Cause cause = builder
                                .build();
                        final ArrayList<Entity> entities = new ArrayList<>();
                        for (EntityItem item : items) {
                            entities.add(EntityUtil.fromNative(item));
                        }
                        final DropItemEvent.Dispense
                                event =
                                SpongeEventFactory.createDropItemEventDispense(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            for (Entity entity : event.getEntities()) {
                                causeTracker.getMixinWorld().forceSpawnEntity(entity);
                            }
                        }
                    });
            phaseContext.getCapturedEntitySupplier()
                    .ifPresentAndNotEmpty(entities -> {
                        final Cause.Builder builder = Cause.source(BlockSpawnCause.builder()
                                .block(blockSnapshot)
                                .type(InternalSpawnTypes.DISPENSE)
                                .build());
                        phaseContext.getNotifier()
                                .ifPresent(builder::notifier);
                        phaseContext.getOwner()
                                .ifPresent(builder::owner);

                        final Cause cause = builder
                                .build();
                        final SpawnEntityEvent
                                event =
                                SpongeEventFactory.createSpawnEntityEvent(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(event);
                        final User user = phaseContext.getNotifier().orElseGet(() -> phaseContext.getOwner().orElse(null));
                        if (!event.isCancelled()) {
                            for (Entity entity : event.getEntities()) {
                                if (user != null) {
                                    EntityUtil.toMixin(entity).setCreator(user.getUniqueId());
                                }
                                causeTracker.getMixinWorld().forceSpawnEntity(entity);
                            }
                        }
                    });
        } else if (state == State.PISTON_MOVING) {
            final List<BlockSnapshot> capturedBlocks = phaseContext.getCapturedBlocks();
            if (!TrackingUtil.processBlockCaptures(capturedBlocks, causeTracker, state, phaseContext)) {
                phaseContext.firstNamed(InternalNamedCauses.Piston.DUMMY_CALLBACK, MutableWrapper.class)
                        .map(wrapper -> ((MutableWrapper<CallbackInfoReturnable<Boolean>>) wrapper).getObj())
                        .ifPresent(callback -> callback.setReturnValue(false));
            }
        }

    }

    @Override
    public boolean spawnEntityOrCapture(IPhaseState phaseState, PhaseContext context, Entity entity, int chunkX, int chunkZ) {
        return this.allowEntitySpawns(phaseState)
               ? context.getCapturedEntities().add(entity)
               : super.spawnEntityOrCapture(phaseState, context, entity, chunkX, chunkZ);
    }

    @Override
    public boolean isRestoring(IPhaseState state, PhaseContext phaseContext, int updateFlag) {
        return state == State.RESTORING_BLOCKS && (updateFlag & 1) == 0;
    }

}
