package ru.justnanix.bfcrusher.bot.entity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IJumpingMount;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.StatBase;
import net.minecraft.tileentity.CommandBlockBaseLogic;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntityStructure;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;
import ru.justnanix.bfcrusher.bot.network.BPlayHandler;

import javax.annotation.Nullable;
import java.util.List;

public class BPlayer extends AbstractClientPlayer {
    public final BPlayHandler connection;
    private int permissionLevel = 0;

    public String currentContainerName = "";

    /**
     * The last X position which was transmitted to the server, used to determine when the X position changes and needs
     * to be re-trasmitted
     */
    private double lastReportedPosX;

    /**
     * The last Y position which was transmitted to the server, used to determine when the Y position changes and needs
     * to be re-transmitted
     */
    private double lastReportedPosY;

    /**
     * The last Z position which was transmitted to the server, used to determine when the Z position changes and needs
     * to be re-transmitted
     */
    private double lastReportedPosZ;

    /**
     * The last yaw value which was transmitted to the server, used to determine when the yaw changes and needs to be
     * re-transmitted
     */
    private float lastReportedYaw;

    /**
     * The last pitch value which was transmitted to the server, used to determine when the pitch changes and needs to
     * be re-transmitted
     */
    private float lastReportedPitch;
    private boolean prevOnGround;
    private boolean field_175170_bN;

    /**
     * the last sprinting state sent to the server
     */
    private boolean serverSprintState;

    /**
     * Reset to 0 every time position is sent to the server, used to send periodic updates every 20 ticks even when the
     * player is not moving.
     */
    private int positionUpdateTicks;
    private boolean hasValidHealth;

    private String serverBrand;
    public MovementInput movementInput;

    protected int sprintToggleTimer;
    public int sprintingTicksLeft;
    private int horseJumpPowerCounter;
    private float horseJumpPower;

    public float timeInPortal;
    public float prevTimeInPortal;

    private boolean handActive;
    private EnumHand activeHand;

    private boolean rowingBoat;
    private boolean autoJumpEnabled = true;
    private int autoJumpTime;
    private boolean wasFallFlying;

    public BPlayer(BPlayHandler handler) {
        super(handler.getWorld(), handler.getGameProfile());
        this.connection = handler;
        this.dimension = 0;
    }

    /**
     * Called when the entity is attacked.
     */
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    /**
     * Heal living entity (param: amount of half-hearts)
     */
    public void heal(float healAmount) {
    }

    public boolean startRiding(Entity entityIn, boolean force) {
        if (!super.startRiding(entityIn, force)) {
            return false;
        } else {
            if (entityIn instanceof EntityBoat) {
                this.prevRotationYaw = entityIn.rotationYaw;
                this.rotationYaw = entityIn.rotationYaw;
                this.setRotationYawHead(entityIn.rotationYaw);
            }

            return true;
        }
    }

    /**
     * Dismounts this entity from the entity it is riding.
     */
    public void stopRiding() {
        super.stopRiding();
        this.rowingBoat = false;
    }

    /**
     * interpolated look vector
     */
    public Vec3d getLook(float partialTicks) {
        return this.getVectorForRotation(this.rotationPitch, this.rotationYaw);
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void tick() {
        if (this.world.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ))) {
            super.tick();

            if (this.isPassenger()) {
                this.connection.sendPacket(new CPacketPlayer.Rotation(this.rotationYaw, this.rotationPitch, this.onGround));
                this.connection.sendPacket(new CPacketInput(this.moveStrafing, this.moveForward, this.movementInput.jump, this.movementInput.sneak));
                Entity entity = this.getLowestRidingEntity();

                if (entity != this && entity.canPassengerSteer()) {
                    this.connection.sendPacket(new CPacketVehicleMove(entity));
                }
            } else {
                this.onUpdateWalkingPlayer();
            }
        }
    }

    /**
     * called every tick when the player is on foot. Performs all the things that normally happen during movement.
     */
    private void onUpdateWalkingPlayer() {
        boolean flag = this.isSprinting();

        if (flag != this.serverSprintState) {
            if (flag) {
                this.connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.START_SPRINTING));
            } else {
                this.connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.STOP_SPRINTING));
            }

            this.serverSprintState = flag;
        }

        boolean flag1 = this.func_70093_af();

        if (flag1 != this.field_175170_bN) {
            if (flag1) {
                this.connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.START_SNEAKING));
            } else {
                this.connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.STOP_SNEAKING));
            }

            this.field_175170_bN = flag1;
        }


        AxisAlignedBB axisalignedbb = this.getBoundingBox();
        double d0 = this.posX - this.lastReportedPosX;
        double d1 = axisalignedbb.minY - this.lastReportedPosY;
        double d2 = this.posZ - this.lastReportedPosZ;
        double d3 = this.rotationYaw - this.lastReportedYaw;
        double d4 = this.rotationPitch - this.lastReportedPitch;
        ++this.positionUpdateTicks;
        boolean flag2 = d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4D || this.positionUpdateTicks >= 20;
        boolean flag3 = d3 != 0.0D || d4 != 0.0D;

        if (this.isPassenger()) {
            this.connection.sendPacket(new CPacketPlayer.PositionRotation(this.field_70159_w, -999.0D, this.field_70179_y, this.rotationYaw, this.rotationPitch, this.onGround));
            flag2 = false;
        } else if (flag2 && flag3) {
            this.connection.sendPacket(new CPacketPlayer.PositionRotation(this.posX, axisalignedbb.minY, this.posZ, this.rotationYaw, this.rotationPitch, this.onGround));
        } else if (flag2) {
            this.connection.sendPacket(new CPacketPlayer.Position(this.posX, axisalignedbb.minY, this.posZ, this.onGround));
        } else if (flag3) {
            this.connection.sendPacket(new CPacketPlayer.Rotation(this.rotationYaw, this.rotationPitch, this.onGround));
        } else if (this.prevOnGround != this.onGround) {
            this.connection.sendPacket(new CPacketPlayer(this.onGround));
        }

        if (flag2) {
            this.lastReportedPosX = this.posX;
            this.lastReportedPosY = axisalignedbb.minY;
            this.lastReportedPosZ = this.posZ;
            this.positionUpdateTicks = 0;
        }

        if (flag3) {
            this.lastReportedYaw = this.rotationYaw;
            this.lastReportedPitch = this.rotationPitch;
        }

        this.prevOnGround = this.onGround;
    }

    @Nullable
    public EntityItem func_71040_bB(boolean p_71040_1_) {
        CPacketPlayerDigging.Action cpacketplayerdigging$action = p_71040_1_ ? CPacketPlayerDigging.Action.DROP_ALL_ITEMS : CPacketPlayerDigging.Action.DROP_ITEM;
        this.connection.sendPacket(new CPacketPlayerDigging(cpacketplayerdigging$action, BlockPos.ZERO, EnumFacing.DOWN));
        return null;
    }

    protected ItemStack func_184816_a(EntityItem p_184816_1_) {
        return ItemStack.EMPTY;
    }

    /**
     * Sends a chat message from the player.
     */
    public void sendChatMessage(String message) {
        this.connection.sendPacket(new CPacketChatMessage(message));
    }

    public void swingArm(EnumHand hand) {
        super.swingArm(hand);
        this.connection.sendPacket(new CPacketAnimation(hand));
    }

    public void respawnPlayer() {
        this.connection.sendPacket(new CPacketClientStatus(CPacketClientStatus.State.PERFORM_RESPAWN));
    }

    /**
     * Deals damage to the entity. This will take the armor of the entity into consideration before damaging the health
     * bar.
     */
    protected void damageEntity(DamageSource damageSrc, float damageAmount) {
        if (!this.isInvulnerableTo(damageSrc)) {
            this.setHealth(this.getHealth() - damageAmount);
        }
    }

    /**
     * set current crafting inventory back to the 2x2 square
     */
    public void closeScreen() {
        this.connection.sendPacket(new CPacketCloseWindow(this.openContainer.windowId));
        this.closeScreenAndDropStack();
    }

    public void closeScreenAndDropStack() {
        this.inventory.setItemStack(ItemStack.EMPTY);
        super.closeScreen();
    }

    /**
     * Updates health locally.
     */
    public void setPlayerSPHealth(float health) {
        if (this.hasValidHealth) {
            float f = this.getHealth() - health;

            if (f <= 0.0F) {
                this.setHealth(health);

                if (f < 0.0F) {
                    this.hurtResistantTime = this.maxHurtResistantTime / 2;
                }
            } else {
                this.lastDamage = f;
                this.setHealth(this.getHealth());
                this.hurtResistantTime = this.maxHurtResistantTime;
                this.damageEntity(DamageSource.GENERIC, f);
                this.maxHurtTime = 10;
                this.hurtTime = this.maxHurtTime;
            }
        } else {
            this.setHealth(health);
            this.hasValidHealth = true;
        }
    }

    /**
     * Adds a value to a statistic field.
     */
    public void addStat(StatBase stat, int amount) {
        if (stat != null) {
            if (stat.field_75972_f) {
                super.addStat(stat, amount);
            }
        }
    }

    /**
     * Sends the player's abilities to the server (if there is one).
     */
    public void sendPlayerAbilities() {
        this.connection.sendPacket(new CPacketPlayerAbilities(this.abilities));
    }

    /**
     * returns true if this is an EntityPlayerSP, or the logged in player.
     */
    public boolean isUser() {
        return true;
    }

    protected void sendHorseJump() {
        this.connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.START_RIDING_JUMP, MathHelper.floor(this.getHorseJumpPower() * 100.0F)));
    }

    public void sendHorseInventory() {
        this.connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.OPEN_INVENTORY));
    }

    /**
     * Sets the brand of the currently connected server. Server brand information is sent over the {@code MC|Brand}
     * plugin channel, and is used to identify modded servers in crash reports.
     */
    public void setServerBrand(String brand) {
        this.serverBrand = brand;
    }

    /**
     * Gets the brand of the currently connected server. May be null if the server hasn't yet sent brand information.
     * Server brand information is sent over the {@code MC|Brand} plugin channel, and is used to identify modded servers
     * in crash reports.
     */
    public String getServerBrand() {
        return this.serverBrand;
    }

    public int getPermissionLevel() {
        return this.permissionLevel;
    }

    public void setPermissionLevel(int p_184839_1_) {
        this.permissionLevel = p_184839_1_;
    }

    public void sendStatusMessage(ITextComponent chatComponent, boolean actionBar) {
    }

    protected boolean func_145771_j(double p_145771_1_, double p_145771_3_, double p_145771_5_) {
        if (this.noClip) {
            return false;
        } else {
            BlockPos blockpos = new BlockPos(p_145771_1_, p_145771_3_, p_145771_5_);
            double d0 = p_145771_1_ - (double) blockpos.getX();
            double d1 = p_145771_5_ - (double) blockpos.getZ();

            if (!this.func_175162_d(blockpos)) {
                int i = -1;
                double d2 = 9999.0D;

                if (this.func_175162_d(blockpos.west()) && d0 < d2) {
                    d2 = d0;
                    i = 0;
                }

                if (this.func_175162_d(blockpos.east()) && 1.0D - d0 < d2) {
                    d2 = 1.0D - d0;
                    i = 1;
                }

                if (this.func_175162_d(blockpos.north()) && d1 < d2) {
                    d2 = d1;
                    i = 4;
                }

                if (this.func_175162_d(blockpos.south()) && 1.0D - d1 < d2) {
                    d2 = 1.0D - d1;
                    i = 5;
                }

                float f = 0.1F;

                if (i == 0) {
                    this.field_70159_w = -0.10000000149011612D;
                }

                if (i == 1) {
                    this.field_70159_w = 0.10000000149011612D;
                }

                if (i == 4) {
                    this.field_70179_y = -0.10000000149011612D;
                }

                if (i == 5) {
                    this.field_70179_y = 0.10000000149011612D;
                }
            }

            return false;
        }
    }

    private boolean func_175162_d(BlockPos p_175162_1_) {
        return !this.world.getBlockState(p_175162_1_).func_185915_l() && !this.world.getBlockState(p_175162_1_.up()).func_185915_l();
    }

    /**
     * Set sprinting switch for Entity.
     */
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        this.sprintingTicksLeft = 0;
    }

    /**
     * Sets the current XP, total XP, and level number.
     */
    public void setXPStats(float currentXP, int maxXP, int level) {
        this.experience = currentXP;
        this.experienceTotal = maxXP;
        this.experienceLevel = level;
    }

    public void sendMessage(ITextComponent component) {
    }

    public boolean func_70003_b(int p_70003_1_, String p_70003_2_) {
        return p_70003_1_ <= this.getPermissionLevel();
    }

    /**
     * Handler for {@link World#setEntityState}
     */
    public void handleStatusUpdate(byte id) {
        if (id >= 24 && id <= 28) {
            this.setPermissionLevel(id - 24);
        } else {
            super.handleStatusUpdate(id);
        }
    }

    /**
     * Get the position in the world. <b>{@code null} is not allowed!</b> If you are not an entity in the world, return
     * the coordinates 0, 0, 0
     */
    public BlockPos getPosition() {
        return new BlockPos(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D);
    }

    public void playSound(SoundEvent soundIn, float volume, float pitch) {
        this.world.playSound(this.posX, this.posY, this.posZ, soundIn, this.getSoundCategory(), volume, pitch, false);
    }

    /**
     * Returns whether the entity is in a server world
     */
    public boolean isServerWorld() {
        return true;
    }

    public void setActiveHand(EnumHand hand) {
        ItemStack itemstack = this.getHeldItem(hand);

        if (!itemstack.isEmpty() && !this.isHandActive()) {
            super.setActiveHand(hand);
            this.handActive = true;
            this.activeHand = hand;
        }
    }

    public boolean isHandActive() {
        return this.handActive;
    }

    public void resetActiveHand() {
        super.resetActiveHand();
        this.handActive = false;
    }

    public EnumHand getActiveHand() {
        return this.activeHand;
    }

    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);

        if (LIVING_FLAGS.equals(key)) {
            boolean flag = (((Byte) this.dataManager.get(LIVING_FLAGS)).byteValue() & 1) > 0;
            EnumHand enumhand = (((Byte) this.dataManager.get(LIVING_FLAGS)).byteValue() & 2) > 0 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;

            if (flag && !this.handActive) {
                this.setActiveHand(enumhand);
            } else if (!flag && this.handActive) {
                this.resetActiveHand();
            }
        }

        if (FLAGS.equals(key) && this.isElytraFlying() && !this.wasFallFlying) {
        }
    }

    public boolean isRidingHorse() {
        Entity entity = this.getRidingEntity();
        return this.isPassenger() && entity instanceof IJumpingMount && ((IJumpingMount) entity).canJump();
    }

    public float getHorseJumpPower() {
        return this.horseJumpPower;
    }

    public void openSignEditor(TileEntitySign signTile) {}

    public void openMinecartCommandBlock(CommandBlockBaseLogic commandBlock) {}

    public void openCommandBlock(TileEntityCommandBlock commandBlock) {}

    public void openStructureBlock(TileEntityStructure structure) {}

    public void openBook(ItemStack stack, EnumHand hand) {}

    public void openInventory(IInventory inventory, String name) {
        String s = inventory instanceof IInteractionObject ? ((IInteractionObject) inventory).func_174875_k() : "minecraft:container";

        if ("minecraft:chest".equals(s)) {
            this.openContainer = new ContainerChest(this.inventory, inventory, this, name);
        } else if ("minecraft:hopper".equals(s)) {
            this.openContainer = new ContainerHopper(this.inventory, inventory, this);
        } else if ("minecraft:furnace".equals(s)) {
            this.openContainer = new ContainerFurnace(this.inventory, inventory);
        } else if ("minecraft:brewing_stand".equals(s)) {
            this.openContainer = new ContainerBrewingStand(this.inventory, inventory);
        } else if ("minecraft:beacon".equals(s)) {
            this.openContainer = new ContainerBeacon(this.inventory, inventory);
        } else if (!"minecraft:dispenser".equals(s) && !"minecraft:dropper".equals(s)) {
            if ("minecraft:shulker_box".equals(s)) {
                this.openContainer = new ContainerShulkerBox(this.inventory, inventory, this);
            }
        } else {
            this.openContainer = new ContainerDispenser(this.inventory, inventory);
        }
    }

    public void openHorseInventory(AbstractHorse horse, IInventory inventoryIn) {}

    public void func_180468_a(IInteractionObject p_180468_1_) {}

    public void func_180472_a(IMerchant p_180472_1_) {}

    public void onCriticalHit(Entity entityHit) {}

    public void onEnchantmentCritical(Entity entityHit) {}

    public boolean func_70093_af() {
        boolean flag = this.movementInput != null && this.movementInput.sneak;
        return flag && !this.field_71083_bS;
    }

    public void updateEntityActionState() {
        super.updateEntityActionState();

        this.moveStrafing = this.movementInput.moveStrafe;
        this.moveForward = this.movementInput.moveForward;
        this.isJumping = this.movementInput.jump;
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons
     * use this to react to sunlight and start to burn.
     */
    public void livingTick() {
        ++this.sprintingTicksLeft;

        if (this.sprintToggleTimer > 0) {
            --this.sprintToggleTimer;
        }

        this.prevTimeInPortal = this.timeInPortal;

        if (this.inPortal) {
            this.timeInPortal += 0.0125F;

            if (this.timeInPortal >= 1.0F) {
                this.timeInPortal = 1.0F;
            }

            this.inPortal = false;
        } else if (this.isPotionActive(MobEffects.NAUSEA) && this.getActivePotionEffect(MobEffects.NAUSEA).getDuration() > 60) {
            this.timeInPortal += 0.006666667F;

            if (this.timeInPortal > 1.0F) {
                this.timeInPortal = 1.0F;
            }
        } else {
            if (this.timeInPortal > 0.0F) {
                this.timeInPortal -= 0.05F;
            }

            if (this.timeInPortal < 0.0F) {
                this.timeInPortal = 0.0F;
            }
        }

        if (this.timeUntilPortal > 0) {
            --this.timeUntilPortal;
        }

        boolean flag = this.movementInput.jump;
        boolean flag1 = this.movementInput.sneak;
        float f = 0.8F;
        boolean flag2 = this.movementInput.moveForward >= 0.8F;
        this.movementInput.updatePlayerMoveState();

        if (this.isHandActive() && !this.isPassenger()) {
            this.movementInput.moveStrafe *= 0.2F;
            this.movementInput.moveForward *= 0.2F;
            this.sprintToggleTimer = 0;
        }

        boolean flag3 = false;

        if (this.autoJumpTime > 0) {
            --this.autoJumpTime;
            flag3 = true;
            this.movementInput.jump = true;
        }

        AxisAlignedBB axisalignedbb = this.getBoundingBox();
        this.func_145771_j(this.posX - (double) this.field_70130_N * 0.35D, axisalignedbb.minY + 0.5D, this.posZ + (double) this.field_70130_N * 0.35D);
        this.func_145771_j(this.posX - (double) this.field_70130_N * 0.35D, axisalignedbb.minY + 0.5D, this.posZ - (double) this.field_70130_N * 0.35D);
        this.func_145771_j(this.posX + (double) this.field_70130_N * 0.35D, axisalignedbb.minY + 0.5D, this.posZ - (double) this.field_70130_N * 0.35D);
        this.func_145771_j(this.posX + (double) this.field_70130_N * 0.35D, axisalignedbb.minY + 0.5D, this.posZ + (double) this.field_70130_N * 0.35D);
        boolean flag4 = (float) this.getFoodStats().getFoodLevel() > 6.0F || this.abilities.allowFlying;

        if (this.onGround && !flag1 && !flag2 && this.movementInput.moveForward >= 0.8F && !this.isSprinting() && flag4 && !this.isHandActive() && !this.isPotionActive(MobEffects.BLINDNESS)) {
            this.setSprinting(true);
        }

        if (!this.isSprinting() && this.movementInput.moveForward >= 0.8F && flag4 && !this.isHandActive() && !this.isPotionActive(MobEffects.BLINDNESS)) {
            this.setSprinting(true);
        }

        if (this.isSprinting() && (this.movementInput.moveForward < 0.8F || this.collidedHorizontally || !flag4)) {
            this.setSprinting(false);
        }

        if (this.abilities.allowFlying) {
            if (this.connection.getController().isSpectatorMode()) {
                if (!this.abilities.isFlying) {
                    this.abilities.isFlying = true;
                    this.sendPlayerAbilities();
                }
            } else if (!flag && this.movementInput.jump && !flag3) {
                if (this.flyToggleTimer == 0) {
                    this.flyToggleTimer = 7;
                } else {
                    this.abilities.isFlying = !this.abilities.isFlying;
                    this.sendPlayerAbilities();
                    this.flyToggleTimer = 0;
                }
            }
        }

        if (this.movementInput.jump && !flag && !this.onGround && this.field_70181_x < 0.0D && !this.isElytraFlying() && !this.abilities.isFlying) {
            ItemStack itemstack = this.getItemStackFromSlot(EntityEquipmentSlot.CHEST);

            if (itemstack.getItem() == Items.ELYTRA && ItemElytra.isUsable(itemstack)) {
                this.connection.sendPacket(new CPacketEntityAction(this, CPacketEntityAction.Action.START_FALL_FLYING));
            }
        }

        this.wasFallFlying = this.isElytraFlying();

        if (this.abilities.isFlying) {
            if (this.movementInput.sneak) {
                this.movementInput.moveStrafe = (float) ((double) this.movementInput.moveStrafe / 0.3D);
                this.movementInput.moveForward = (float) ((double) this.movementInput.moveForward / 0.3D);
                this.field_70181_x -= (double) (this.abilities.getFlySpeed() * 3.0F);
            }

            if (this.movementInput.jump) {
                this.field_70181_x += (double) (this.abilities.getFlySpeed() * 3.0F);
            }
        }

        if (this.isRidingHorse()) {
            IJumpingMount ijumpingmount = (IJumpingMount) this.getRidingEntity();

            if (this.horseJumpPowerCounter < 0) {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter == 0) {
                    this.horseJumpPower = 0.0F;
                }
            }

            if (flag && !this.movementInput.jump) {
                this.horseJumpPowerCounter = -10;
                ijumpingmount.setJumpPower(MathHelper.floor(this.getHorseJumpPower() * 100.0F));
                this.sendHorseJump();
            } else if (!flag && this.movementInput.jump) {
                this.horseJumpPowerCounter = 0;
                this.horseJumpPower = 0.0F;
            } else if (flag) {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter < 10) {
                    this.horseJumpPower = (float) this.horseJumpPowerCounter * 0.1F;
                } else {
                    this.horseJumpPower = 0.8F + 2.0F / (float) (this.horseJumpPowerCounter - 9) * 0.1F;
                }
            }
        } else {
            this.horseJumpPower = 0.0F;
        }

        super.livingTick();

        if (this.onGround && this.abilities.isFlying && !this.connection.getController().isSpectatorMode()) {
            this.abilities.isFlying = false;
            this.sendPlayerAbilities();
        }
    }

    /**
     * Handles updating while riding another entity
     */
    public void updateRidden() {
        super.updateRidden();
        this.rowingBoat = false;

        if (this.getRidingEntity() instanceof EntityBoat) {
            EntityBoat entityboat = (EntityBoat) this.getRidingEntity();
            entityboat.updateInputs(this.movementInput.leftKeyDown, this.movementInput.rightKeyDown, this.movementInput.forwardKeyDown, this.movementInput.backKeyDown);
            this.rowingBoat |= this.movementInput.leftKeyDown || this.movementInput.rightKeyDown || this.movementInput.forwardKeyDown || this.movementInput.backKeyDown;
        }
    }

    public boolean isRowingBoat() {
        return this.rowingBoat;
    }

    @Nullable

    /**
     * Removes the given potion effect from the active potion map and returns it. Does not call cleanup callbacks for
     * the end of the potion effect.
     */
    public PotionEffect removeActivePotionEffect(@Nullable Potion potioneffectin) {
        if (potioneffectin == MobEffects.NAUSEA) {
            this.prevTimeInPortal = 0.0F;
            this.timeInPortal = 0.0F;
        }

        return super.removeActivePotionEffect(potioneffectin);
    }

    public void func_70091_d(MoverType p_70091_1_, double p_70091_2_, double p_70091_4_, double p_70091_6_) {
        double d0 = this.posX;
        double d1 = this.posZ;
        super.func_70091_d(p_70091_1_, p_70091_2_, p_70091_4_, p_70091_6_);
        this.updateAutoJump((float) (this.posX - d0), (float) (this.posZ - d1));
    }

    public boolean isAutoJumpEnabled() {
        return this.autoJumpEnabled;
    }

    protected void updateAutoJump(float p_189810_1_, float p_189810_2_) {
        if (this.isAutoJumpEnabled()) {
            if (this.autoJumpTime <= 0 && this.onGround && !this.func_70093_af() && !this.isPassenger()) {
                Vec2f vec2f = this.movementInput.getMoveVector();

                if (vec2f.x != 0.0F || vec2f.y != 0.0F) {
                    Vec3d vec3d = new Vec3d(this.posX, this.getBoundingBox().minY, this.posZ);
                    double d0 = this.posX + (double) p_189810_1_;
                    double d1 = this.posZ + (double) p_189810_2_;
                    Vec3d vec3d1 = new Vec3d(d0, this.getBoundingBox().minY, d1);
                    Vec3d vec3d2 = new Vec3d((double) p_189810_1_, 0.0D, (double) p_189810_2_);
                    float f = this.getAIMoveSpeed();
                    float f1 = (float) vec3d2.lengthSquared();

                    if (f1 <= 0.001F) {
                        float f2 = f * vec2f.x;
                        float f3 = f * vec2f.y;
                        float f4 = MathHelper.sin(this.rotationYaw * 0.017453292F);
                        float f5 = MathHelper.cos(this.rotationYaw * 0.017453292F);
                        vec3d2 = new Vec3d((double) (f2 * f5 - f3 * f4), vec3d2.y, (double) (f3 * f5 + f2 * f4));
                        f1 = (float) vec3d2.lengthSquared();

                        if (f1 <= 0.001F) {
                            return;
                        }
                    }

                    float f12 = (float) MathHelper.fastInvSqrt((double) f1);
                    Vec3d vec3d12 = vec3d2.scale((double) f12);
                    Vec3d vec3d13 = this.getForward();
                    float f13 = (float) (vec3d13.x * vec3d12.x + vec3d13.z * vec3d12.z);

                    if (f13 >= -0.15F) {
                        BlockPos blockpos = new BlockPos(this.posX, this.getBoundingBox().maxY, this.posZ);
                        IBlockState iblockstate = this.world.getBlockState(blockpos);

                        if (iblockstate.func_185890_d(this.world, blockpos) == null) {
                            blockpos = blockpos.up();
                            IBlockState iblockstate1 = this.world.getBlockState(blockpos);

                            if (iblockstate1.func_185890_d(this.world, blockpos) == null) {
                                float f6 = 7.0F;
                                float f7 = 1.2F;

                                if (this.isPotionActive(MobEffects.JUMP_BOOST)) {
                                    f7 += (float) (this.getActivePotionEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.75F;
                                }

                                float f8 = Math.max(f * 7.0F, 1.0F / f12);
                                Vec3d vec3d4 = vec3d1.add(vec3d12.scale((double) f8));
                                float f9 = this.field_70130_N;
                                float f10 = this.field_70131_O;
                                AxisAlignedBB axisalignedbb = (new AxisAlignedBB(vec3d, vec3d4.add(0.0D, (double) f10, 0.0D))).grow((double) f9, 0.0D, (double) f9);
                                Vec3d lvt_19_1_ = vec3d.add(0.0D, 0.5099999904632568D, 0.0D);
                                vec3d4 = vec3d4.add(0.0D, 0.5099999904632568D, 0.0D);
                                Vec3d vec3d5 = vec3d12.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
                                Vec3d vec3d6 = vec3d5.scale((double) (f9 * 0.5F));
                                Vec3d vec3d7 = lvt_19_1_.subtract(vec3d6);
                                Vec3d vec3d8 = vec3d4.subtract(vec3d6);
                                Vec3d vec3d9 = lvt_19_1_.add(vec3d6);
                                Vec3d vec3d10 = vec3d4.add(vec3d6);
                                List<AxisAlignedBB> list = this.world.func_184144_a(this, axisalignedbb);

                                if (!list.isEmpty()) {
                                    ;
                                }

                                float f11 = Float.MIN_VALUE;
                                label86:

                                for (AxisAlignedBB axisalignedbb2 : list) {
                                    if (axisalignedbb2.intersects(vec3d7, vec3d8) || axisalignedbb2.intersects(vec3d9, vec3d10)) {
                                        f11 = (float) axisalignedbb2.maxY;
                                        Vec3d vec3d11 = axisalignedbb2.getCenter();
                                        BlockPos blockpos1 = new BlockPos(vec3d11);
                                        int i = 1;

                                        while (true) {
                                            if ((float) i >= f7) {
                                                break label86;
                                            }

                                            BlockPos blockpos2 = blockpos1.up(i);
                                            IBlockState iblockstate2 = this.world.getBlockState(blockpos2);
                                            AxisAlignedBB axisalignedbb1;

                                            if ((axisalignedbb1 = iblockstate2.func_185890_d(this.world, blockpos2)) != null) {
                                                f11 = (float) axisalignedbb1.maxY + (float) blockpos2.getY();

                                                if ((double) f11 - this.getBoundingBox().minY > (double) f7) {
                                                    return;
                                                }
                                            }

                                            if (i > 1) {
                                                blockpos = blockpos.up();
                                                IBlockState iblockstate3 = this.world.getBlockState(blockpos);

                                                if (iblockstate3.func_185890_d(this.world, blockpos) != null) {
                                                    return;
                                                }
                                            }

                                            ++i;
                                        }
                                    }
                                }

                                if (f11 != Float.MIN_VALUE) {
                                    float f14 = (float) ((double) f11 - this.getBoundingBox().minY);

                                    if (f14 > 0.5F && f14 <= f7) {
                                        this.autoJumpTime = 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
