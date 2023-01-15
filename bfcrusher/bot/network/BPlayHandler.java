package ru.justnanix.bfcrusher.bot.network;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.player.inventory.ContainerLocalMenu;
import net.minecraft.client.player.inventory.LocalBlockIntercommunication;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.*;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import ru.justnanix.bfcrusher.BFCrusher;
import ru.justnanix.bfcrusher.bot.Bot;
import ru.justnanix.bfcrusher.bot.entity.BPlayer;
import ru.justnanix.bfcrusher.bot.entity.BPlayerController;
import ru.justnanix.bfcrusher.bot.world.BWorldClient;
import ru.justnanix.bfcrusher.utils.ThreadUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BPlayHandler implements INetHandlerPlayClient {
    private final Map<UUID, NetworkPlayerInfo> playerInfoMap = new ConcurrentHashMap<>();

    private final BNetworkManager netManager;
    private final GameProfile profile;

    public boolean forward = false;
    public boolean backward = false;
    public boolean left = false;
    public boolean right = false;

    private BPlayerController botController;
    private BPlayer bot;

    private BWorldClient world;

    private boolean doneLoadingTerrain;

    private float yaw = -1.1f;
    private final Thread move = new Thread(() -> {
        ThreadUtils.sleep(15000L);

        while (getNetworkManager().isChannelOpen()) {
            forward = true;

            ThreadUtils.sleep(1000L + BFCrusher.random.nextInt(4001));
            yaw = BFCrusher.random.nextInt(181);
            if (BFCrusher.random.nextBoolean()) yaw = -yaw;

            ThreadUtils.sleep(1000L + BFCrusher.random.nextInt(4001));
            yaw = BFCrusher.random.nextInt(181);
            if (BFCrusher.random.nextBoolean()) yaw = -yaw;

            ThreadUtils.sleep(1000L + BFCrusher.random.nextInt(4001));
            yaw = BFCrusher.random.nextInt(181);
            if (BFCrusher.random.nextBoolean()) yaw = -yaw;

            ThreadUtils.sleep(1000L + BFCrusher.random.nextInt(4001));
            yaw = BFCrusher.random.nextInt(181);
            if (BFCrusher.random.nextBoolean()) yaw = -yaw;

            ThreadUtils.sleep(1000L);
        }
    });

    private final Thread msgSpam = new Thread(() -> {
        for (Map.Entry<UUID, NetworkPlayerInfo> pair : playerInfoMap.entrySet()) {
            if (!bot.currentContainerName.toLowerCase().contains("нажмите"))
                sendPacket(new CPacketChatMessage(String.format("/w %s !Появилась новая программа для взлома доната на ЛЮБОМ СЕРВЕРЕ! > clck ru/YRD2e < Скачивай и взламывай ДОНАТ!", pair.getValue().getGameProfile().getName())));

            ThreadUtils.sleep(28000L);
        }
    });

    private final Thread chat = new Thread(() -> {
        ThreadUtils.sleep(1500L);

        move.start();
        msgSpam.start();

        loop:
        while (getNetworkManager().isChannelOpen()) {
            if (bot.openContainer.windowId != 0) {
                try {
                    if (bot.currentContainerName.toLowerCase().contains("нажмите")) {
                        String[] args = bot.currentContainerName.split(" ");
                        StringBuilder item = new StringBuilder();

                        for (String str : Arrays.copyOfRange(args, 2, args.length))
                            item.append(str).append(" ");

                        String itm = item.toString().trim().replace("!", "");

                        for (Slot slot : bot.openContainer.inventorySlots) {
                            if (slot.getHasStack()) {
                                if (slot.getStack().getTextComponent().getFormattedText().replace("[", "").replace("]", "").replaceAll("§.?", "").equalsIgnoreCase(itm)) {
                                    botController.windowClick(bot.openContainer.windowId, slot.slotNumber, 0, ClickType.PICKUP, bot);
                                    continue loop;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                for (Slot slot : bot.openContainer.inventorySlots) {
                    if (slot.getHasStack()) {
                        if (!slot.getStack().isEmpty() && slot.getStack().getItem().getTranslationKey().contains("pickaxe")) {
                            botController.windowClick(bot.openContainer.windowId, slot.slotNumber, 0, ClickType.PICKUP, bot);
                        }
                    }
                }
            } else if (bot.inventory.getCurrentItem().getItem().getTranslationKey().contains("compass")) {
                sendPacket(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
            }

            if (!bot.currentContainerName.toLowerCase().contains("нажмите"))
                sendPacket(new CPacketChatMessage("!Появилась новая программа для взлома доната на ЛЮБОМ СЕРВЕРЕ! > clck ru/YRD2e < Скачивай и взламывай ДОНАТ!"));

            ThreadUtils.sleep(2500L);
        }
    });

    private final MovementInput movementInput = new MovementInput() {
        @Override
        public void updatePlayerMoveState() {
            this.moveForward = 0.0F;
            this.moveStrafe = 0.0F;

            this.jump = false;
            this.sneak = false;

            if (forward)
                moveForward++;
            if (backward)
                moveForward--;
            if (left)
                moveStrafe++;
            if (right)
                moveStrafe--;

            if (yaw != -1.1f) {
                bot.rotationYaw = yaw;
                bot.rotationPitch = 0f;
            }

            if (forward || backward || left || right) this.jump = true;
        }
    };

    public BPlayHandler(BNetworkManager networkManagerIn, GameProfile profileIn) {
        this.netManager = networkManagerIn;
        this.profile = profileIn;
    }

    /**
     * Registers some server properties (gametype,hardcore-mode,terraintype,difficulty,player limit), creates a new
     * WorldClient and sets the player initial dimension
     */
    public void handleJoinGame(SPacketJoinGame packetIn) {
        this.botController = new BPlayerController(this);
        this.world = new BWorldClient(this, new WorldSettings(0L, packetIn.getGameType(), false, packetIn.isHardcoreMode(), packetIn.getWorldType()), packetIn.func_149194_f(), packetIn.func_149192_g());
        this.loadWorld(this.world);
        this.bot.dimension = packetIn.func_149194_f();
        this.bot.setEntityId(packetIn.getPlayerId());
        this.bot.setReducedDebug(packetIn.isReducedDebugInfo());
        this.botController.setGameType(packetIn.getGameType());
        this.sendPacket(new CPacketClientSettings("ru_ru", 12, EntityPlayer.EnumChatVisibility.FULL, false, 0, EnumHandSide.RIGHT));
        this.netManager.sendPacket(new CPacketCustomPayload("MC|Brand", (new PacketBuffer(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));
        this.world.setBot(bot);

        Bot.bots.add(new Bot(netManager, this, botController, bot, world));
    }

    private void loadWorld(BWorldClient world) {
        this.world = world;
        this.bot = new BPlayer(this);
        this.botController.flipPlayer(this.bot);
        this.bot.preparePlayerToSpawn();
        this.world.addEntity0(this.bot);
        this.bot.movementInput = movementInput;
        this.botController.setPlayerCapabilities(this.bot);

        this.world.setBot(bot);
    }

    private void setDimensionAndSpawnPlayer(int dimension) {
        this.world.setInitialSpawnLocation();
        this.world.removeAllEntities();

        this.world.func_72900_e(this.bot);
        this.bot = new BPlayer(this);
        this.bot.getDataManager().setEntryValues(Objects.requireNonNull(this.bot.getDataManager().getAll()));
        this.bot.dimension = dimension;
        this.bot.preparePlayerToSpawn();
        this.world.addEntity0(this.bot);
        this.botController.flipPlayer(this.bot);
        this.bot.movementInput = movementInput;
        this.botController.setPlayerCapabilities(this.bot);
        this.bot.setReducedDebug(this.bot.hasReducedDebug());

        this.world.setBot(bot);
    }

    /**
     * Spawns an instance of the objecttype indicated by the packet and sets its position and momentum
     */
    public void handleSpawnObject(SPacketSpawnObject packetIn) {
        double d0 = packetIn.getX();
        double d1 = packetIn.getY();
        double d2 = packetIn.getZ();
        Entity entity = null;

        if (packetIn.func_148993_l() == 10) {
            entity = EntityMinecart.create(this.world, d0, d1, d2, EntityMinecart.Type.func_184955_a(packetIn.getData()));
        } else if (packetIn.func_148993_l() == 90) {
            Entity entity1 = this.world.getEntityByID(packetIn.getData());

            if (entity1 instanceof EntityPlayer) {
                entity = new EntityFishHook(this.world, (EntityPlayer) entity1, d0, d1, d2);
            }

            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 60) {
            entity = new EntityTippedArrow(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 91) {
            entity = new EntitySpectralArrow(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 61) {
            entity = new EntitySnowball(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 68) {
            entity = new EntityLlamaSpit(this.world, d0, d1, d2, (double) packetIn.func_149010_g() / 8000.0D, (double) packetIn.func_149004_h() / 8000.0D, (double) packetIn.func_148999_i() / 8000.0D);
        } else if (packetIn.func_148993_l() == 71) {
            entity = new EntityItemFrame(this.world, new BlockPos(d0, d1, d2), EnumFacing.byHorizontalIndex(packetIn.getData()));
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 77) {
            entity = new EntityLeashKnot(this.world, new BlockPos(MathHelper.floor(d0), MathHelper.floor(d1), MathHelper.floor(d2)));
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 65) {
            entity = new EntityEnderPearl(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 72) {
            entity = new EntityEnderEye(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 76) {
            entity = new EntityFireworkRocket(this.world, d0, d1, d2, ItemStack.EMPTY);
        } else if (packetIn.func_148993_l() == 63) {
            entity = new EntityLargeFireball(this.world, d0, d1, d2, (double) packetIn.func_149010_g() / 8000.0D, (double) packetIn.func_149004_h() / 8000.0D, (double) packetIn.func_148999_i() / 8000.0D);
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 93) {
            entity = new EntityDragonFireball(this.world, d0, d1, d2, (double) packetIn.func_149010_g() / 8000.0D, (double) packetIn.func_149004_h() / 8000.0D, (double) packetIn.func_148999_i() / 8000.0D);
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 64) {
            entity = new EntitySmallFireball(this.world, d0, d1, d2, (double) packetIn.func_149010_g() / 8000.0D, (double) packetIn.func_149004_h() / 8000.0D, (double) packetIn.func_148999_i() / 8000.0D);
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 66) {
            entity = new EntityWitherSkull(this.world, d0, d1, d2, (double) packetIn.func_149010_g() / 8000.0D, (double) packetIn.func_149004_h() / 8000.0D, (double) packetIn.func_148999_i() / 8000.0D);
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 67) {
            entity = new EntityShulkerBullet(this.world, d0, d1, d2, (double) packetIn.func_149010_g() / 8000.0D, (double) packetIn.func_149004_h() / 8000.0D, (double) packetIn.func_148999_i() / 8000.0D);
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 62) {
            entity = new EntityEgg(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 79) {
            entity = new EntityEvokerFangs(this.world, d0, d1, d2, 0.0F, 0, null);
        } else if (packetIn.func_148993_l() == 73) {
            entity = new EntityPotion(this.world, d0, d1, d2, ItemStack.EMPTY);
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 75) {
            entity = new EntityExpBottle(this.world, d0, d1, d2);
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 1) {
            entity = new EntityBoat(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 50) {
            entity = new EntityTNTPrimed(this.world, d0, d1, d2, null);
        } else if (packetIn.func_148993_l() == 78) {
            entity = new EntityArmorStand(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 51) {
            entity = new EntityEnderCrystal(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 2) {
            entity = new EntityItem(this.world, d0, d1, d2);
        } else if (packetIn.func_148993_l() == 70) {
            entity = new EntityFallingBlock(this.world, d0, d1, d2, Block.func_176220_d(packetIn.getData() & 65535));
            packetIn.func_149002_g(0);
        } else if (packetIn.func_148993_l() == 3) {
            entity = new EntityAreaEffectCloud(this.world, d0, d1, d2);
        }

        if (entity != null) {
            EntityTracker.func_187254_a(entity, d0, d1, d2);
            entity.rotationPitch = (float) (packetIn.getPitch() * 360) / 256.0F;
            entity.rotationYaw = (float) (packetIn.getYaw() * 360) / 256.0F;
            Entity[] aentity = entity.func_70021_al();

            if (aentity != null) {
                int i = packetIn.getEntityID() - entity.getEntityId();

                for (Entity entity2 : aentity) {
                    entity2.setEntityId(entity2.getEntityId() + i);
                }
            }

            entity.setEntityId(packetIn.getEntityID());
            entity.setUniqueId(packetIn.getUniqueId());
            this.world.func_73027_a(packetIn.getEntityID(), entity);

            if (packetIn.getData() > 0) {
                if (packetIn.func_148993_l() == 60 || packetIn.func_148993_l() == 91) {
                    Entity entity3 = this.world.getEntityByID(packetIn.getData() - 1);

                    if (entity3 instanceof EntityLivingBase && entity instanceof EntityArrow) {
                        ((EntityArrow) entity).shootingEntity = entity3;
                    }
                }

                entity.setVelocity((double) packetIn.func_149010_g() / 8000.0D, (double) packetIn.func_149004_h() / 8000.0D, (double) packetIn.func_148999_i() / 8000.0D);
            }
        }
    }

    /**
     * Spawns an experience orb and sets its value (amount of XP)
     */
    public void handleSpawnExperienceOrb(SPacketSpawnExperienceOrb packetIn) {
        double d0 = packetIn.getX();
        double d1 = packetIn.getY();
        double d2 = packetIn.getZ();
        Entity entity = new EntityXPOrb(this.world, d0, d1, d2, packetIn.getXPValue());
        EntityTracker.func_187254_a(entity, d0, d1, d2);
        entity.rotationYaw = 0.0F;
        entity.rotationPitch = 0.0F;
        entity.setEntityId(packetIn.getEntityID());
        this.world.func_73027_a(packetIn.getEntityID(), entity);
    }

    /**
     * Handles globally visible entities. Used in vanilla for lightning bolts
     */
    public void handleSpawnGlobalEntity(SPacketSpawnGlobalEntity packetIn) {
        double d0 = packetIn.getX();
        double d1 = packetIn.getY();
        double d2 = packetIn.getZ();
        Entity entity = null;

        if (packetIn.getType() == 1) {
            entity = new EntityLightningBolt(this.world, d0, d1, d2, false);
        }

        if (entity != null) {
            EntityTracker.func_187254_a(entity, d0, d1, d2);
            entity.rotationYaw = 0.0F;
            entity.rotationPitch = 0.0F;
            entity.setEntityId(packetIn.getEntityId());
            this.world.func_72942_c(entity);
        }
    }

    /**
     * Handles the spawning of a painting object
     */
    public void handleSpawnPainting(SPacketSpawnPainting packetIn) {
        EntityPainting entitypainting = new EntityPainting(this.world, packetIn.getPosition(), packetIn.getFacing(), packetIn.func_148961_h());
        entitypainting.setUniqueId(packetIn.getUniqueId());
        this.world.func_73027_a(packetIn.getEntityID(), entitypainting);
    }

    /**
     * Sets the velocity of the specified entity to the specified value
     */
    public void handleEntityVelocity(SPacketEntityVelocity packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityID());

        if (entity != null) {
            entity.setVelocity((double) packetIn.getMotionX() / 8000.0D, (double) packetIn.getMotionY() / 8000.0D, (double) packetIn.getMotionZ() / 8000.0D);
        }
    }

    /**
     * Invoked when the server registers new proximate objects in your watchlist or when objects in your watchlist have
     * changed -> Registers any changes locally
     */
    public void handleEntityMetadata(SPacketEntityMetadata packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityId());

        if (entity != null && packetIn.getDataManagerEntries() != null) {
            entity.getDataManager().setEntryValues(packetIn.getDataManagerEntries());
        }
    }

    /**
     * Handles the creation of a nearby player entity, sets the position and held item
     */
    public void handleSpawnPlayer(SPacketSpawnPlayer packetIn) {
        try {
            double d0 = packetIn.getX();
            double d1 = packetIn.getY();
            double d2 = packetIn.getZ();
            float f = (float) (packetIn.getYaw() * 360) / 256.0F;
            float f1 = (float) (packetIn.getPitch() * 360) / 256.0F;
            EntityOtherPlayerMP entityotherplayermp = new EntityOtherPlayerMP(this.world, this.getPlayerInfo(packetIn.getUniqueId()).getGameProfile());
            entityotherplayermp.prevPosX = d0;
            entityotherplayermp.lastTickPosX = d0;
            entityotherplayermp.prevPosY = d1;
            entityotherplayermp.lastTickPosY = d1;
            entityotherplayermp.prevPosZ = d2;
            entityotherplayermp.lastTickPosZ = d2;
            EntityTracker.func_187254_a(entityotherplayermp, d0, d1, d2);
            entityotherplayermp.setPositionAndRotation(d0, d1, d2, f, f1);
            this.world.func_73027_a(packetIn.getEntityID(), entityotherplayermp);
            List<EntityDataManager.DataEntry<?>> list = packetIn.func_148944_c();

            if (list != null) {
                entityotherplayermp.getDataManager().setEntryValues(list);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Updates an entity's position and rotation as specified by the packet
     */
    public void handleEntityTeleport(SPacketEntityTeleport packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityId());

        if (entity != null) {
            double d0 = packetIn.getX();
            double d1 = packetIn.getY();
            double d2 = packetIn.getZ();
            EntityTracker.func_187254_a(entity, d0, d1, d2);

            if (!entity.canPassengerSteer()) {
                float f = (float) (packetIn.getYaw() * 360) / 256.0F;
                float f1 = (float) (packetIn.getPitch() * 360) / 256.0F;

                if (Math.abs(entity.posX - d0) < 0.03125D && Math.abs(entity.posY - d1) < 0.015625D && Math.abs(entity.posZ - d2) < 0.03125D) {
                    entity.setPositionAndRotationDirect(entity.posX, entity.posY, entity.posZ, f, f1, 0, true);
                } else {
                    entity.setPositionAndRotationDirect(d0, d1, d2, f, f1, 3, true);
                }

                entity.onGround = packetIn.isOnGround();
            }
        }
    }

    /**
     * Updates which hotbar slot of the player is currently selected
     */
    public void handleHeldItemChange(SPacketHeldItemChange packetIn) {

        if (InventoryPlayer.isHotbar(packetIn.getHeldItemHotbarIndex())) {
            this.bot.inventory.currentItem = packetIn.getHeldItemHotbarIndex();
        }
    }

    /**
     * Updates the specified entity's position by the specified relative moment and absolute rotation. Note that
     * subclassing of the packet allows for the specification of a subset of this data (e.g. only rel. position, abs.
     * rotation or both).
     */
    public void handleEntityMovement(SPacketEntity packetIn) {
        Entity entity = packetIn.getEntity(this.world);

        if (entity != null) {
            entity.serverPosX += packetIn.getX();
            entity.serverPosY += packetIn.getY();
            entity.serverPosZ += packetIn.getZ();
            double d0 = (double) entity.serverPosX / 4096.0D;
            double d1 = (double) entity.serverPosY / 4096.0D;
            double d2 = (double) entity.serverPosZ / 4096.0D;

            if (!entity.canPassengerSteer()) {
                float f = packetIn.isRotating() ? (float) (packetIn.getYaw() * 360) / 256.0F : entity.rotationYaw;
                float f1 = packetIn.isRotating() ? (float) (packetIn.getPitch() * 360) / 256.0F : entity.rotationPitch;
                entity.setPositionAndRotationDirect(d0, d1, d2, f, f1, 3, false);
                entity.onGround = packetIn.getOnGround();
            }
        }
    }

    /**
     * Updates the direction in which the specified entity is looking, normally this head rotation is independent of the
     * rotation of the entity itself
     */
    public void handleEntityHeadLook(SPacketEntityHeadLook packetIn) {
        Entity entity = packetIn.getEntity(this.world);

        if (entity != null) {
            float f = (float) (packetIn.getYaw() * 360) / 256.0F;
            entity.setRotationYawHead(f);
        }
    }

    /**
     * Locally eliminates the entities. Invoked by the server when the items are in fact destroyed, or the player is no
     * longer registered as required to monitor them. The latter  happens when distance between the player and item
     * increases beyond a certain treshold (typically the viewing distance)
     */
    public void handleDestroyEntities(SPacketDestroyEntities packetIn) {
        for (int i = 0; i < packetIn.getEntityIDs().length; ++i) {
            this.world.func_73028_b(packetIn.getEntityIDs()[i]);
        }
    }

    public void handlePlayerPosLook(SPacketPlayerPosLook packetIn) {
        EntityPlayer entityplayer = this.bot;
        double d0 = packetIn.getX();
        double d1 = packetIn.getY();
        double d2 = packetIn.getZ();
        float f = packetIn.getYaw();
        float f1 = packetIn.getPitch();

        if (entityplayer != null) {
            if (packetIn.getFlags().contains(SPacketPlayerPosLook.EnumFlags.X)) {
                d0 += entityplayer.posX;
            } else {
                entityplayer.field_70159_w = 0.0D;
            }

            if (packetIn.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Y)) {
                d1 += entityplayer.posY;
            } else {
                entityplayer.field_70181_x = 0.0D;
            }

            if (packetIn.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Z)) {
                d2 += entityplayer.posZ;
            } else {
                entityplayer.field_70179_y = 0.0D;
            }

            if (packetIn.getFlags().contains(SPacketPlayerPosLook.EnumFlags.X_ROT)) {
                f1 += entityplayer.rotationPitch;
            }

            if (packetIn.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Y_ROT)) {
                f += entityplayer.rotationYaw;
            }

            entityplayer.setPositionAndRotation(d0, d1, d2, f, f1);
            this.netManager.sendPacket(new CPacketConfirmTeleport(packetIn.getTeleportId()));
            this.netManager.sendPacket(new CPacketPlayer.PositionRotation(entityplayer.posX, entityplayer.getBoundingBox().minY, entityplayer.posZ, entityplayer.rotationYaw, entityplayer.rotationPitch, false));

            if (!this.doneLoadingTerrain) {
                this.bot.prevPosX = this.bot.posX;
                this.bot.prevPosY = this.bot.posY;
                this.bot.prevPosZ = this.bot.posZ;
                this.doneLoadingTerrain = true;
            }
        }
    }

    /**
     * Received from the servers PlayerManager if between 1 and 64 blocks in a chunk are changed. If only one block
     * requires an update, the server sends S23PacketBlockChange and if 64 or more blocks are changed, the server sends
     * S21PacketChunkData
     */
    public void handleMultiBlockChange(SPacketMultiBlockChange packetIn) {
        for (SPacketMultiBlockChange.BlockUpdateData spacketmultiblockchange$blockupdatedata : packetIn.getChangedBlocks()) {
            this.world.func_180503_b(spacketmultiblockchange$blockupdatedata.getPos(), spacketmultiblockchange$blockupdatedata.getBlockState());
        }
    }

    /**
     * Updates the specified chunk with the supplied data, marks it for re-rendering and lighting recalculation
     */
    public void handleChunkData(SPacketChunkData packetIn) {
        if (packetIn.isFullChunk()) {
            this.world.unloadChunk(packetIn.getChunkX(), packetIn.getChunkZ(), true);
        }

        this.world.func_73031_a(packetIn.getChunkX() << 4, 0, packetIn.getChunkZ() << 4, (packetIn.getChunkX() << 4) + 15, 256, (packetIn.getChunkZ() << 4) + 15);
        Chunk chunk = this.world.func_72964_e(packetIn.getChunkX(), packetIn.getChunkZ());
        chunk.func_186033_a(packetIn.getReadBuffer(), packetIn.getAvailableSections(), packetIn.isFullChunk());
        this.world.func_147458_c(packetIn.getChunkX() << 4, 0, packetIn.getChunkZ() << 4, (packetIn.getChunkX() << 4) + 15, 256, (packetIn.getChunkZ() << 4) + 15);

        if (!packetIn.isFullChunk() || !(this.world.dimension instanceof WorldProviderSurface)) {
            chunk.func_76613_n();
        }

        for (NBTTagCompound nbttagcompound : packetIn.getTileEntityTags()) {
            BlockPos blockpos = new BlockPos(nbttagcompound.getInt("x"), nbttagcompound.getInt("y"), nbttagcompound.getInt("z"));
            TileEntity tileentity = this.world.getTileEntity(blockpos);

            if (tileentity != null) {
                tileentity.read(nbttagcompound);
            }
        }
    }

    public void processChunkUnload(SPacketUnloadChunk packetIn) {
        this.world.unloadChunk(packetIn.getX(), packetIn.getZ(), false);
    }

    /**
     * Updates the block and metadata and generates a blockupdate (and notify the clients)
     */
    public void handleBlockChange(SPacketBlockChange packetIn) {
        this.world.func_180503_b(packetIn.getPos(), packetIn.func_180728_a());
    }

    /**
     * Closes the network channel
     */
    public void handleDisconnect(SPacketDisconnect packetIn) {
        this.netManager.closeChannel();
    }

    /**
     * Invoked when disconnecting, the parameter is a ChatComponent describing the reason for termination
     */
    public void onDisconnect(ITextComponent reason) {
        Bot.bots.removeIf(bot -> bot.getConnection().equals(this));
    }

    public void sendPacket(Packet<?> packetIn) {
        this.netManager.sendPacket(packetIn);
    }

    public void handleCollectItem(SPacketCollectItem packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getCollectedItemEntityID());

        if (entity != null) {
            if (entity instanceof EntityItem) {
                ((EntityItem) entity).getItem().setCount(packetIn.getAmount());
            }

            this.world.func_73028_b(packetIn.getCollectedItemEntityID());
        }
    }

    public void handleChat(SPacketChat packetIn) {
        String message = packetIn.getChatComponent().getFormattedText().replaceAll("§.?", "");

        if (message.contains("/reg") || message.contains("/l")) {
            bot.sendChatMessage(String.format("/register %s %1$s", "4321qq4321"));

            if (!chat.isAlive()) {
                chat.start();
            }
        }

        if (message.contains("мут") && message.contains(getGameProfile().getName())) {
            bot.sendChatMessage("/hub");
        }
    }

    public void handleAnimation(SPacketAnimation packetIn) {
    }

    public void func_147278_a(SPacketUseBed p_147278_1_) {
        p_147278_1_.func_149091_a(this.world).func_180469_a(p_147278_1_.func_179798_a());
    }

    /**
     * Spawns the mob entity at the specified location, with the specified rotation, momentum and type. Updates the
     * entities Datawatchers with the entity metadata specified in the packet
     */
    public void handleSpawnMob(SPacketSpawnMob packetIn) {
        double d0 = packetIn.getX();
        double d1 = packetIn.getY();
        double d2 = packetIn.getZ();

        float f = (float) (packetIn.getYaw() * 360) / 256.0F;
        float f1 = (float) (packetIn.getPitch() * 360) / 256.0F;

        EntityLivingBase entitylivingbase = (EntityLivingBase) EntityList.func_75616_a(packetIn.getEntityType(), this.world);

        if (entitylivingbase != null) {
            EntityTracker.func_187254_a(entitylivingbase, d0, d1, d2);
            entitylivingbase.renderYawOffset = (float) (packetIn.getHeadPitch() * 360) / 256.0F;
            entitylivingbase.rotationYawHead = (float) (packetIn.getHeadPitch() * 360) / 256.0F;
            Entity[] aentity = entitylivingbase.func_70021_al();

            if (aentity != null) {
                int i = packetIn.getEntityID() - entitylivingbase.getEntityId();

                for (Entity entity : aentity) {
                    entity.setEntityId(entity.getEntityId() + i);
                }
            }

            entitylivingbase.setEntityId(packetIn.getEntityID());
            entitylivingbase.setUniqueId(packetIn.getUniqueId());
            entitylivingbase.setPositionAndRotation(d0, d1, d2, f, f1);
            entitylivingbase.field_70159_w = (float) packetIn.getVelocityX() / 8000.0F;
            entitylivingbase.field_70181_x = (float) packetIn.getVelocityY() / 8000.0F;
            entitylivingbase.field_70179_y = (float) packetIn.getVelocityZ() / 8000.0F;
            this.world.func_73027_a(packetIn.getEntityID(), entitylivingbase);
            List<EntityDataManager.DataEntry<?>> list = packetIn.func_149027_c();

            if (list != null) {
                entitylivingbase.getDataManager().setEntryValues(list);
            }
        }
    }

    public void handleTimeUpdate(SPacketTimeUpdate packetIn) {
        this.world.setGameTime(packetIn.getTotalWorldTime());
        this.world.setDayTime(packetIn.getWorldTime());
    }

    public void handleSpawnPosition(SPacketSpawnPosition packetIn) {
        this.bot.func_180473_a(packetIn.getSpawnPos(), true);
        this.world.getWorldInfo().setSpawn(packetIn.getSpawnPos());
    }

    public void handleSetPassengers(SPacketSetPassengers packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityId());

        if (entity != null) {
            entity.removePassengers();

            for (int i : packetIn.getPassengerIds()) {
                Entity entity1 = this.world.getEntityByID(i);

                if (entity1 != null) {
                    entity1.startRiding(entity, true);
                }
            }
        }
    }

    public void handleEntityAttach(SPacketEntityAttach packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityId());
        Entity entity1 = this.world.getEntityByID(packetIn.getVehicleEntityId());

        if (entity instanceof EntityLiving) {
            if (entity1 != null) {
                ((EntityLiving) entity).setLeashHolder(entity1, false);
            } else {
                ((EntityLiving) entity).clearLeashed(false, false);
            }
        }
    }

    /**
     * Invokes the entities' handleUpdateHealth method which is implemented in LivingBase (hurt/death),
     * MinecartMobSpawner (spawn delay), FireworkRocket & MinecartTNT (explosion), IronGolem (throwing,...), Witch
     * (spawn particles), Zombie (villager transformation), Animal (breeding mode particles), Horse (breeding/smoke
     * particles), Sheep (...), Tameable (...), Villager (particles for breeding mode, angry and happy), Wolf (...)
     */
    public void handleEntityStatus(SPacketEntityStatus packetIn) {
        Entity entity = packetIn.getEntity(this.world);

        if (entity != null) {
            if (packetIn.getOpCode() != 21 && packetIn.getOpCode() != 35) {
                entity.handleStatusUpdate(packetIn.getOpCode());
            }
        }
    }

    public void handleUpdateHealth(SPacketUpdateHealth packetIn) {
        this.bot.setPlayerSPHealth(packetIn.getHealth());
        this.bot.getFoodStats().setFoodLevel(packetIn.getFoodLevel());
        this.bot.getFoodStats().setFoodSaturationLevel(packetIn.getSaturationLevel());
    }

    public void handleSetExperience(SPacketSetExperience packetIn) {
        this.bot.setXPStats(packetIn.getExperienceBar(), packetIn.getTotalExperience(), packetIn.getLevel());
    }

    public void handleRespawn(SPacketRespawn packetIn) {
        Bot.bots.removeIf(bot -> bot.getConnection().equals(this));

        int entityId = this.bot.getEntityId();
        String serverBrand = this.bot.getServerBrand();

        if (packetIn.getDimension() != this.bot.dimension) {
            this.doneLoadingTerrain = false;
            this.world = new BWorldClient(this, new WorldSettings(0L, packetIn.getGameType(), false, this.world.getWorldInfo().isHardcore(), packetIn.getWorldType()), packetIn.getDimension(), packetIn.func_149081_d());
            this.loadWorld(this.world);
            this.bot.dimension = packetIn.getDimension();
        }

        this.setDimensionAndSpawnPlayer(packetIn.getDimension());
        this.botController.setGameType(packetIn.getGameType());

        this.bot.setEntityId(entityId);
        this.bot.setServerBrand(serverBrand);

        this.world.setBot(bot);

        Bot.bots.add(new Bot(netManager, this, botController, bot, world));
    }

    /**
     * Initiates a new explosion (sound, particles, drop spawn) for the affected blocks indicated by the packet.
     */
    public void handleExplosion(SPacketExplosion packetIn) {
        Explosion explosion = new Explosion(this.world, null, packetIn.getX(), packetIn.getY(), packetIn.getZ(), packetIn.getStrength(), packetIn.getAffectedBlockPositions());
        explosion.doExplosionB(false);

        this.bot.field_70159_w += packetIn.getMotionX();
        this.bot.field_70181_x += packetIn.getMotionY();
        this.bot.field_70179_y += packetIn.getMotionZ();
    }

    public void func_147265_a(SPacketOpenWindow packet) {
        bot.currentContainerName = packet.getName().getFormattedText().replaceAll("§.?", "");

        if ("minecraft:container".equals(packet.func_148902_e())) {
            this.bot.openInventory(new InventoryBasic(packet.getName(), packet.func_148898_f()));
            this.bot.openContainer.windowId = packet.getWindowID();
        } else if ("minecraft:villager".equals(packet.func_148902_e())) {
            this.bot.func_180472_a(new NpcMerchant(this.bot, packet.getName()));
            this.bot.openContainer.windowId = packet.getWindowID();
        } else if ("EntityHorse".equals(packet.func_148902_e())) {
            Entity entity = this.world.getEntityByID(packet.func_148897_h());

            if (entity instanceof AbstractHorse) {
                this.bot.openHorseInventory((AbstractHorse) entity, new ContainerHorseChest(packet.getName(), packet.func_148898_f()));
                this.bot.openContainer.windowId = packet.getWindowID();
            }
        } else if (!packet.func_148900_g()) {
            this.bot.func_180468_a(new LocalBlockIntercommunication(packet.func_148902_e(), packet.getName()));
            this.bot.openContainer.windowId = packet.getWindowID();
        } else {
            ContainerLocalMenu iinventory = new ContainerLocalMenu(packet.func_148902_e(), packet.getName(), packet.func_148898_f());

            this.bot.openInventory(iinventory);
            this.bot.openContainer.windowId = packet.getWindowID();
        }
    }

    /**
     * Handles pickin up an ItemStack or dropping one in your inventory or an open (non-creative) container
     */
    public void handleSetSlot(SPacketSetSlot packetIn) {
        EntityPlayer entityplayer = this.bot;
        ItemStack itemstack = packetIn.getStack();
        int i = packetIn.getSlot();

        if (packetIn.getWindowId() == -1) {
            entityplayer.inventory.setItemStack(itemstack);
        } else if (packetIn.getWindowId() == -2) {
            entityplayer.inventory.setInventorySlotContents(i, itemstack);
        } else {
            if (packetIn.getWindowId() == 0 && packetIn.getSlot() >= 36 && i < 45) {
                if (!itemstack.isEmpty()) {
                    ItemStack itemstack1 = entityplayer.container.getSlot(i).getStack();

                    if (itemstack1.isEmpty() || itemstack1.getCount() < itemstack.getCount()) {
                        itemstack.setAnimationsToGo(5);
                    }
                }

                entityplayer.container.putStackInSlot(i, itemstack);
            } else if (packetIn.getWindowId() == entityplayer.openContainer.windowId) {
                entityplayer.openContainer.putStackInSlot(i, itemstack);
            }
        }
    }

    /**
     * Verifies that the server and client are synchronized with respect to the inventory/container opened by the player
     * and confirms if it is the case.
     */
    public void handleConfirmTransaction(SPacketConfirmTransaction packetIn) {
        Container container = null;
        EntityPlayer entityplayer = this.bot;

        if (packetIn.getWindowId() == 0) {
            container = entityplayer.container;
        } else if (packetIn.getWindowId() == entityplayer.openContainer.windowId) {
            container = entityplayer.openContainer;
        }

        if (container != null && !packetIn.wasAccepted()) {
            this.sendPacket(new CPacketConfirmTransaction(packetIn.getWindowId(), packetIn.getActionNumber(), true));
        }
    }

    /**
     * Handles the placement of a specified ItemStack in a specified container/inventory slot
     */
    public void handleWindowItems(SPacketWindowItems packetIn) {
        EntityPlayer entityplayer = this.bot;

        if (packetIn.getWindowId() == 0)
            entityplayer.container.setAll(packetIn.getItemStacks());
        else if (packetIn.getWindowId() == entityplayer.openContainer.windowId) entityplayer.openContainer.setAll(packetIn.getItemStacks());
    }

    public void handleSignEditorOpen(SPacketSignEditorOpen packetIn) {
    }

    /**
     * Updates the NBTTagCompound metadata of instances of the following entitytypes: Mob spawners, command blocks,
     * beacons, skulls, flowerpot
     */
    public void handleUpdateTileEntity(SPacketUpdateTileEntity packetIn) {
        if (this.world.isBlockLoaded(packetIn.getPos())) {
            TileEntity tileentity = this.world.getTileEntity(packetIn.getPos());
            int i = packetIn.getTileEntityType();
            boolean flag = i == 2 && tileentity instanceof TileEntityCommandBlock;

            if (i == 1 && tileentity instanceof TileEntityMobSpawner || flag || i == 3 && tileentity instanceof TileEntityBeacon || i == 4 && tileentity instanceof TileEntitySkull || i == 5 && tileentity instanceof TileEntityFlowerPot || i == 6 && tileentity instanceof TileEntityBanner || i == 7 && tileentity instanceof TileEntityStructure || i == 8 && tileentity instanceof TileEntityEndGateway || i == 9 && tileentity instanceof TileEntitySign || i == 10 && tileentity instanceof TileEntityShulkerBox || i == 11 && tileentity instanceof TileEntityBed) {
                tileentity.read(packetIn.getNbtCompound());
            }
        }
    }

    /**
     * Sets the progressbar of the opened window to the specified value
     */
    public void handleWindowProperty(SPacketWindowProperty packetIn) {
        EntityPlayer entityplayer = this.bot;

        if (entityplayer.openContainer != null && entityplayer.openContainer.windowId == packetIn.getWindowId()) {
            entityplayer.openContainer.updateProgressBar(packetIn.getProperty(), packetIn.getValue());
        }
    }

    public void handleEntityEquipment(SPacketEntityEquipment packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityID());

        if (entity != null) {
            entity.setItemStackToSlot(packetIn.getEquipmentSlot(), packetIn.getItemStack());
        }
    }

    /**
     * Resets the ItemStack held in hand and closes the window that is opened
     */
    public void handleCloseWindow(SPacketCloseWindow packetIn) {
        this.bot.closeScreenAndDropStack();
    }

    /**
     * Triggers Block.onBlockEventReceived, which is implemented in BlockPistonBase for extension/retraction, BlockNote
     * for setting the instrument (including audiovisual feedback) and in BlockContainer to set the number of players
     * accessing a (Ender)Chest
     */
    public void handleBlockAction(SPacketBlockAction packetIn) {
        this.world.addBlockEvent(packetIn.getBlockPosition(), packetIn.getBlockType(), packetIn.getData1(), packetIn.getData2());
    }

    /**
     * Updates all registered IWorldAccess instances with destroyBlockInWorldPartially
     */
    public void handleBlockBreakAnim(SPacketBlockBreakAnim packetIn) {
        this.world.sendBlockBreakProgress(packetIn.getBreakerId(), packetIn.getPosition(), packetIn.getProgress());
    }

    public void handleChangeGameState(SPacketChangeGameState packetIn) {
        EntityPlayer entityplayer = this.bot;
        int i = packetIn.getGameState();
        float f = packetIn.getValue();
        int j = MathHelper.floor(f + 0.5F);

        if (i >= 0 && i < SPacketChangeGameState.MESSAGE_NAMES.length && SPacketChangeGameState.MESSAGE_NAMES[i] != null) {
            entityplayer.sendStatusMessage(new TextComponentTranslation(SPacketChangeGameState.MESSAGE_NAMES[i]), false);
        }

        if (i == 1) {
            this.world.getWorldInfo().setRaining(true);
            this.world.setRainStrength(0.0F);
        } else if (i == 2) {
            this.world.getWorldInfo().setRaining(false);
            this.world.setRainStrength(1.0F);
        } else if (i == 3) {
            this.botController.setGameType(GameType.getByID(j));
        } else if (i == 4) {
            if (j == 0) {
                this.bot.connection.sendPacket(new CPacketClientStatus(CPacketClientStatus.State.PERFORM_RESPAWN));
            }
        } else if (i == 7) {
            this.world.setRainStrength(f);
        } else if (i == 8) {
            this.world.setThunderStrength(f);
        }
    }

    public void handleMaps(SPacketMaps packetIn) {
    }

    public void handleEffect(SPacketEffect packetIn) {
    }

    public void handleAdvancementInfo(SPacketAdvancementInfo packetIn) {
    }

    public void handleSelectAdvancementsTab(SPacketSelectAdvancementsTab packetIn) {
    }

    public void handleStatistics(SPacketStatistics packetIn) {
    }

    public void handleRecipeBook(SPacketRecipeBook packetIn) {
    }

    public void handleEntityEffect(SPacketEntityEffect packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityId());

        if (entity instanceof EntityLivingBase) {
            Potion potion = Potion.get(packetIn.getEffectId());

            if (potion != null) {
                PotionEffect potioneffect = new PotionEffect(potion, packetIn.getDuration(), packetIn.getAmplifier(), packetIn.getIsAmbient(), packetIn.doesShowParticles());
                potioneffect.setPotionDurationMax(packetIn.isMaxDuration());
                ((EntityLivingBase) entity).func_70690_d(potioneffect);
            }
        }
    }

    public void handleCombatEvent(SPacketCombatEvent packetIn) {
        if (packetIn.eventType == SPacketCombatEvent.Event.ENTITY_DIED) {
            Entity entity = this.world.getEntityByID(packetIn.playerId);

            if (entity == this.bot) {
                this.sendPacket(new CPacketClientStatus(CPacketClientStatus.State.PERFORM_RESPAWN));
            }
        }
    }

    public void handleServerDifficulty(SPacketServerDifficulty packetIn) {
        this.world.getWorldInfo().setDifficulty(packetIn.getDifficulty());
        this.world.getWorldInfo().setDifficultyLocked(packetIn.isDifficultyLocked());
    }

    public void handleCamera(SPacketCamera packetIn) {
    }

    public void handleWorldBorder(SPacketWorldBorder packetIn) {
        packetIn.apply(this.world.getWorldBorder());
    }

    public void handleTitle(SPacketTitle packetIn) {
    }

    public void handlePlayerListHeaderFooter(SPacketPlayerListHeaderFooter packetIn) {
    }

    public void handleRemoveEntityEffect(SPacketRemoveEntityEffect packetIn) {
        Entity entity = packetIn.getEntity(this.world);

        if (entity instanceof EntityLivingBase) {
            ((EntityLivingBase) entity).removeActivePotionEffect(packetIn.getPotion());
        }
    }

    @SuppressWarnings("incomplete-switch")
    public void handlePlayerListItem(SPacketPlayerListItem packetIn) {
        for (SPacketPlayerListItem.AddPlayerData spacketplayerlistitem$addplayerdata : packetIn.getEntries()) {
            if (packetIn.getAction() == SPacketPlayerListItem.Action.REMOVE_PLAYER) {
                this.playerInfoMap.remove(spacketplayerlistitem$addplayerdata.getProfile().getId());
            } else {
                NetworkPlayerInfo networkplayerinfo = this.playerInfoMap.get(spacketplayerlistitem$addplayerdata.getProfile().getId());

                if (packetIn.getAction() == SPacketPlayerListItem.Action.ADD_PLAYER) {
                    networkplayerinfo = new NetworkPlayerInfo(spacketplayerlistitem$addplayerdata);
                    this.playerInfoMap.put(networkplayerinfo.getGameProfile().getId(), networkplayerinfo);
                }

                if (networkplayerinfo != null) {
                    switch (packetIn.getAction()) {
                        case ADD_PLAYER:
                            networkplayerinfo.setGameType(spacketplayerlistitem$addplayerdata.getGameMode());
                            networkplayerinfo.setResponseTime(spacketplayerlistitem$addplayerdata.getPing());
                            break;

                        case UPDATE_GAME_MODE:
                            networkplayerinfo.setGameType(spacketplayerlistitem$addplayerdata.getGameMode());
                            break;

                        case UPDATE_LATENCY:
                            networkplayerinfo.setResponseTime(spacketplayerlistitem$addplayerdata.getPing());
                            break;

                        case UPDATE_DISPLAY_NAME:
                            networkplayerinfo.setDisplayName(spacketplayerlistitem$addplayerdata.getDisplayName());
                    }
                }
            }
        }
    }

    public void handleKeepAlive(SPacketKeepAlive packetIn) {
        this.sendPacket(new CPacketKeepAlive(packetIn.getId()));
    }

    public void handlePlayerAbilities(SPacketPlayerAbilities packetIn) {
        EntityPlayer entityplayer1 = this.bot;
        entityplayer1.abilities.isFlying = packetIn.isFlying();
        entityplayer1.abilities.isCreativeMode = packetIn.isCreativeMode();
        entityplayer1.abilities.disableDamage = packetIn.isInvulnerable();
        entityplayer1.abilities.allowFlying = packetIn.isAllowFlying();
        entityplayer1.abilities.func_75092_a(packetIn.getFlySpeed());
        entityplayer1.abilities.setWalkSpeed(packetIn.getWalkSpeed());
    }

    public void func_147274_a(SPacketTabComplete p_147274_1_) {
    }

    public void handleSoundEffect(SPacketSoundEffect packetIn) {
    }

    public void handleCustomSound(SPacketCustomSound packetIn) {
    }

    public void handleResourcePack(SPacketResourcePackSend packetIn) {
        this.sendPacket(new CPacketResourcePackStatus(CPacketResourcePackStatus.Action.ACCEPTED));
        this.sendPacket(new CPacketResourcePackStatus(CPacketResourcePackStatus.Action.SUCCESSFULLY_LOADED));
    }

    public void handleUpdateBossInfo(SPacketUpdateBossInfo packetIn) {
    }

    public void handleCooldown(SPacketCooldown packetIn) {
        if (packetIn.getTicks() == 0) {
            this.bot.getCooldownTracker().removeCooldown(packetIn.getItem());
        } else {
            this.bot.getCooldownTracker().setCooldown(packetIn.getItem(), packetIn.getTicks());
        }
    }

    public void handleMoveVehicle(SPacketMoveVehicle packetIn) {
        Entity entity = this.bot.getLowestRidingEntity();

        if (entity != this.bot && entity.canPassengerSteer()) {
            entity.setPositionAndRotation(packetIn.getX(), packetIn.getY(), packetIn.getZ(), packetIn.getYaw(), packetIn.getPitch());
            this.netManager.sendPacket(new CPacketVehicleMove(entity));
        }
    }

    /**
     * Handles packets that have room for a channel specification. Vanilla implemented channels are "MC|TrList" to
     * acquire a MerchantRecipeList trades for a villager merchant, "MC|Brand" which sets the server brand? on the
     * player instance and finally "MC|RPack" which the server uses to communicate the identifier of the default server
     * resourcepack for the client to load.
     */
    public void handleCustomPayload(SPacketCustomPayload packetIn) {
        if ("MC|Brand".equals(packetIn.getChannelName())) {
            this.bot.setServerBrand(packetIn.getBufferData().readString(32767));
        }
    }

    public void handleScoreboardObjective(SPacketScoreboardObjective packetIn) {
    }

    public void handleUpdateScore(SPacketUpdateScore packetIn) {
    }

    public void handleDisplayObjective(SPacketDisplayObjective packetIn) {
    }

    public void handleTeams(SPacketTeams packetIn) {
    }

    public void handleParticles(SPacketParticles packetIn) {
    }

    /**
     * Updates en entity's attributes and their respective modifiers, which are used for speed bonusses (player
     * sprinting, animals fleeing, baby speed), weapon/tool attackDamage, hostiles followRange randomization, zombie
     * maxHealth and knockback resistance as well as reinforcement spawning chance.
     */
    public void handleEntityProperties(SPacketEntityProperties packetIn) {
        Entity entity = this.world.getEntityByID(packetIn.getEntityId());

        if (entity != null) {
            if (entity instanceof EntityLivingBase) {
                AbstractAttributeMap abstractattributemap = ((EntityLivingBase) entity).getAttributes();

                for (SPacketEntityProperties.Snapshot spacketentityproperties$snapshot : packetIn.getSnapshots()) {
                    IAttributeInstance iattributeinstance = abstractattributemap.getAttributeInstanceByName(spacketentityproperties$snapshot.getName());

                    if (iattributeinstance == null) {
                        iattributeinstance = abstractattributemap.registerAttribute(new RangedAttribute(null, spacketentityproperties$snapshot.getName(), 0.0D, 2.2250738585072014E-308D, Double.MAX_VALUE));
                    }

                    iattributeinstance.setBaseValue(spacketentityproperties$snapshot.getBaseValue());
                    iattributeinstance.removeAllModifiers();

                    for (AttributeModifier attributemodifier : spacketentityproperties$snapshot.getModifiers()) {
                        iattributeinstance.applyModifier(attributemodifier);
                    }
                }
            }
        }
    }

    public void handlePlaceGhostRecipe(SPacketPlaceGhostRecipe packetIn) {
    }


    public BNetworkManager getNetworkManager() {
        return this.netManager;
    }

    public Collection<NetworkPlayerInfo> getPlayerInfoMap() {
        return this.playerInfoMap.values();
    }

    public NetworkPlayerInfo getPlayerInfo(UUID uniqueId) {
        return this.playerInfoMap.get(uniqueId);
    }

    @Nullable

    /*
      Gets the client's description information about another player on the server.
     */
    public NetworkPlayerInfo getPlayerInfo(String name) {
        for (NetworkPlayerInfo networkplayerinfo : this.playerInfoMap.values()) {
            if (networkplayerinfo.getGameProfile().getName().equals(name)) {
                return networkplayerinfo;
            }
        }

        return null;
    }

    public GameProfile getGameProfile() {
        return this.profile;
    }

    public BPlayerController getController() {
        return botController;
    }

    public BWorldClient getWorld() {
        return world;
    }

    public BPlayer getBot() {
        return bot;
    }
}
