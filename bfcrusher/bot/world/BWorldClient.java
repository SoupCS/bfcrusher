package ru.justnanix.bfcrusher.bot.world;

import com.google.common.collect.Sets;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.audio.MovingSoundMinecart;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.ParticleFirework;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ICrashReportDetail;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveDataMemoryStorage;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;
import ru.justnanix.bfcrusher.bot.entity.BPlayer;
import ru.justnanix.bfcrusher.bot.network.BPlayHandler;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;

public class BWorldClient extends World {
    private final BPlayHandler connection;
    private ChunkProviderClient chunkProviderClient;

    private final Set<Entity> entities = Sets.newHashSet();
    private final Set<Entity> field_73036_L = Sets.newHashSet();

    private final Set<ChunkPos> chunks = Sets.<ChunkPos>newHashSet();
    private final Set<ChunkPos> field_184157_a;
    
    private BPlayer bot;

    public BWorldClient(BPlayHandler handler, WorldSettings worldSettings, int dimension, EnumDifficulty difficulty) {
        super(new SaveHandlerMP(), new WorldInfo(worldSettings, "MpServer"), DimensionType.getById(dimension).func_186070_d(), true);

        this.field_184157_a = Sets.newHashSet();
        this.connection = handler;

        this.getWorldInfo().setDifficulty(difficulty);
        this.setSpawnPoint(new BlockPos(8, 64, 8));

        this.dimension.func_76558_a(this);
        this.chunkProvider = this.func_72970_h();
        this.field_72988_C = new SaveDataMemoryStorage();

        this.calculateInitialSkylight();
        this.calculateInitialWeather();
    }

    /**
     * Runs a single tick for the world
     */
    public void tick() {
        super.tick();
        this.setGameTime(this.getGameTime() + 1L);

        if (this.getGameRules().func_82766_b("doDaylightCycle")) {
            this.setDayTime(this.getDayTime() + 1L);
        }

        for (int i = 0; i < 10 && !this.field_73036_L.isEmpty(); ++i) {
            Entity entity = this.field_73036_L.iterator().next();
            this.field_73036_L.remove(entity);

            if (!this.field_72996_f.contains(entity)) {
                this.addEntity0(entity);
            }
        }

        this.chunkProviderClient.func_73156_b();
        this.func_147456_g();
    }

    public void func_73031_a(int p_73031_1_, int p_73031_2_, int p_73031_3_, int p_73031_4_, int p_73031_5_, int p_73031_6_) {

    }

    protected IChunkProvider func_72970_h() {
        this.chunkProviderClient = new ChunkProviderClient(this);
        return this.chunkProviderClient;
    }

    protected boolean func_175680_a(int p_175680_1_, int p_175680_2_, boolean p_175680_3_) {
        return p_175680_3_ || !this.getChunkProvider().func_186025_d(p_175680_1_, p_175680_2_).isEmpty();
    }

    protected void func_184154_a() {
        this.field_184157_a.clear();

        int i = 12;
        int j = MathHelper.floor(this.bot.posX / 16.0D);
        int k = MathHelper.floor(this.bot.posZ / 16.0D);

        for (int l = -i; l <= i; ++l) {
            for (int i1 = -i; i1 <= i; ++i1) {
                this.field_184157_a.add(new ChunkPos(l + j, i1 + k));
            }
        }
    }

    protected void func_147456_g() {
        this.func_184154_a();

        this.chunks.retainAll(this.field_184157_a);

        if (this.chunks.size() == this.field_184157_a.size()) {
            this.chunks.clear();
        }

        int i = 0;

        for (ChunkPos chunkpos : this.field_184157_a) {
            if (!this.chunks.contains(chunkpos)) {
                int j = chunkpos.x * 16;
                int k = chunkpos.z * 16;
                Chunk chunk = this.func_72964_e(chunkpos.x, chunkpos.z);
                this.func_147467_a(j, k, chunk);
                this.chunks.add(chunkpos);
                ++i;

                if (i >= 10) {
                    return;
                }
            }
        }
    }

    public void unloadChunk(int p_73025_1_, int p_73025_2_, boolean p_73025_3_) {
        if (p_73025_3_) {
            this.chunkProviderClient.func_73158_c(p_73025_1_, p_73025_2_);
        } else {
            this.chunkProviderClient.unloadChunk(p_73025_1_, p_73025_2_);
            this.func_147458_c(p_73025_1_ * 16, 0, p_73025_2_ * 16, p_73025_1_ * 16 + 15, 256, p_73025_2_ * 16 + 15);
        }
    }

    /**
     * Called when an entity is spawned in the world. This includes players.
     */
    public boolean addEntity0(Entity entityIn) {
        boolean flag = super.addEntity0(entityIn);
        this.entities.add(entityIn);

        if (!flag) {
            this.field_73036_L.add(entityIn);
        }

        return flag;
    }

    public void func_72900_e(Entity p_72900_1_) {
        super.func_72900_e(p_72900_1_);
        this.entities.remove(p_72900_1_);
    }

    protected void func_72923_a(Entity p_72923_1_) {
        super.func_72923_a(p_72923_1_);

        if (this.field_73036_L.contains(p_72923_1_)) {
            this.field_73036_L.remove(p_72923_1_);
        }
    }

    protected void func_72847_b(Entity p_72847_1_) {
        super.func_72847_b(p_72847_1_);

        if (this.entities.contains(p_72847_1_)) {
            if (p_72847_1_.isAlive()) {
                this.field_73036_L.add(p_72847_1_);
            } else {
                this.entities.remove(p_72847_1_);
            }
        }
    }

    public void func_73027_a(int p_73027_1_, Entity p_73027_2_) {
        Entity entity = this.getEntityByID(p_73027_1_);

        if (entity != null) {
            this.func_72900_e(entity);
        }

        this.entities.add(p_73027_2_);
        p_73027_2_.setEntityId(p_73027_1_);

        if (!this.addEntity0(p_73027_2_)) {
            this.field_73036_L.add(p_73027_2_);
        }

        this.field_175729_l.func_76038_a(p_73027_1_, p_73027_2_);
    }

    @Nullable

    /**
     * Returns the Entity with the given ID, or null if it doesn't exist in this World.
     */
    public Entity getEntityByID(int id) {
        return id == this.bot.getEntityId() ? this.bot : super.getEntityByID(id);
    }

    public Entity func_73028_b(int p_73028_1_) {
        Entity entity = this.field_175729_l.func_76049_d(p_73028_1_);

        if (entity != null) {
            this.entities.remove(entity);
            this.func_72900_e(entity);
        }

        return entity;
    }

    @Deprecated
    public boolean func_180503_b(BlockPos p_180503_1_, IBlockState p_180503_2_) {
        int i = p_180503_1_.getX();
        int j = p_180503_1_.getY();
        int k = p_180503_1_.getZ();
        this.func_73031_a(i, j, k, i, j, k);
        return super.setBlockState(p_180503_1_, p_180503_2_, 3);
    }

    /**
     * If on MP, sends a quitting packet.
     */
    public void sendQuittingDisconnectingPacket() {
        this.connection.getNetworkManager().closeChannel();
    }

    protected void func_72979_l() {}

    protected void func_147467_a(int p_147467_1_, int p_147467_2_, Chunk p_147467_3_) {}

    public void animateTick(int posX, int posY, int posZ) {}

    public void animateTick(int x, int y, int z, int offset, Random random, boolean holdingBarrier, BlockPos.MutableBlockPos pos) {}

    /**
     * also releases skins.
     */
    public void removeAllEntities() {
        this.field_72996_f.removeAll(this.field_72997_g);

        for (int i = 0; i < this.field_72997_g.size(); ++i) {
            Entity entity = this.field_72997_g.get(i);
            int j = entity.chunkCoordX;
            int k = entity.chunkCoordZ;

            if (entity.addedToChunk && this.func_175680_a(j, k, true)) {
                this.func_72964_e(j, k).removeEntity(entity);
            }
        }

        for (int i1 = 0; i1 < this.field_72997_g.size(); ++i1) {
            this.func_72847_b(this.field_72997_g.get(i1));
        }

        this.field_72997_g.clear();

        for (int j1 = 0; j1 < this.field_72996_f.size(); ++j1) {
            Entity entity1 = this.field_72996_f.get(j1);
            Entity entity2 = entity1.getRidingEntity();

            if (entity2 != null) {
                if (!entity2.removed && entity2.isPassenger(entity1)) {
                    continue;
                }

                entity1.stopRiding();
            }

            if (entity1.removed) {
                int k1 = entity1.chunkCoordX;
                int l = entity1.chunkCoordZ;

                if (entity1.addedToChunk && this.func_175680_a(k1, l, true)) {
                    this.func_72964_e(k1, l).removeEntity(entity1);
                }

                this.field_72996_f.remove(j1--);
                this.func_72847_b(entity1);
            }
        }
    }

    /**
     * Adds some basic stats of the world to the given crash report.
     */
    public CrashReportCategory fillCrashReport(CrashReport report) {
        return null;
    }

    public void playSound(@Nullable EntityPlayer player, double x, double y, double z, SoundEvent soundIn, SoundCategory category, float volume, float pitch) {}

    public void playSound(BlockPos pos, SoundEvent soundIn, SoundCategory category, float volume, float pitch, boolean distanceDelay) {}

    public void playSound(double x, double y, double z, SoundEvent soundIn, SoundCategory category, float volume, float pitch, boolean distanceDelay) {}

    public void makeFireworks(double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable NBTTagCompound compound) {}

    public void sendPacketToServer(Packet<?> packetIn) {
        this.connection.sendPacket(packetIn);
    }

    public void setScoreboard(Scoreboard scoreboardIn) {
        this.field_96442_D = scoreboardIn;
    }

    /**
     * Sets the world time.
     */
    public void setDayTime(long time) {
        if (time < 0L) {
            time = -time;
            this.getGameRules().func_82764_b("doDaylightCycle", "false");
        } else {
            this.getGameRules().func_82764_b("doDaylightCycle", "true");
        }

        super.setDayTime(time);
    }

    /**
     * Gets the world's chunk provider
     */
    public ChunkProviderClient getChunkProvider() {
        return (ChunkProviderClient) super.getChunkProvider();
    }

    public BPlayer getBot() {
        return bot;
    }

    public void setBot(BPlayer bot) {
        this.bot = bot;
    }
}
