package me.bartvv.friends.glowapi;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.inventivetalent.apihelper.API;
import org.inventivetalent.apihelper.APIManager;
import org.inventivetalent.packetlistener.PacketListenerAPI;
import org.inventivetalent.packetlistener.handler.PacketHandler;
import org.inventivetalent.packetlistener.handler.PacketOptions;
import org.inventivetalent.packetlistener.handler.ReceivedPacket;
import org.inventivetalent.packetlistener.handler.SentPacket;
import org.inventivetalent.packetlistener.reflection.minecraft.Minecraft;
import org.inventivetalent.packetlistener.reflection.resolver.ConstructorResolver;
import org.inventivetalent.packetlistener.reflection.resolver.FieldResolver;
import org.inventivetalent.packetlistener.reflection.resolver.MethodResolver;
import org.inventivetalent.packetlistener.reflection.resolver.ResolverQuery;
import org.inventivetalent.packetlistener.reflection.resolver.minecraft.NMSClassResolver;
import org.inventivetalent.packetlistener.reflection.resolver.minecraft.OBCClassResolver;

import me.bartvv.friends.Friends;

public class GlowAPI implements API, Listener {

	private static Map<UUID, GlowData> dataMap = new HashMap<>();

	private static final NMSClassResolver NMS_CLASS_RESOLVER = new NMSClassResolver();

	// Metadata
	private static Class<?> PacketPlayOutEntityMetadata;
	static Class<?> DataWatcher;
	static Class<?> DataWatcherItem;
	private static Class<?> Entity;

	private static FieldResolver PacketPlayOutMetadataFieldResolver;
	private static FieldResolver EntityFieldResolver;
	private static FieldResolver DataWatcherFieldResolver;
	static FieldResolver DataWatcherItemFieldResolver;

	private static ConstructorResolver DataWatcherItemConstructorResolver;

	private static MethodResolver DataWatcherMethodResolver;
	static MethodResolver DataWatcherItemMethodResolver;
	private static MethodResolver EntityMethodResolver;

	// Scoreboard
	private static Class<?> PacketPlayOutScoreboardTeam;

	private static FieldResolver PacketScoreboardTeamFieldResolver;

	// Packets
	private static FieldResolver EntityPlayerFieldResolver;
	private static MethodResolver PlayerConnectionMethodResolver;

	// Options
	/**
	 * Default name-tag visibility (always, hideForOtherTeams, hideForOwnTeam,
	 * never)
	 */
	public static String TEAM_TAG_VISIBILITY = "always";
	/**
	 * Default push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 */
	public static String TEAM_PUSH = "always";

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity
	 *            {@link Entity} to update
	 * @param color
	 *            {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or
	 *            <code>null</code> to stop glowing
	 * @param tagVisibility
	 *            visibility of the name-tag (always, hideForOtherTeams,
	 *            hideForOwnTeam, never)
	 * @param push
	 *            push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 * @param receiver
	 *            {@link Player} that will see the update
	 */
	public static void setGlowing(Entity entity, Color color, String tagVisibility, String push, Player receiver) {
		if (receiver == null) {
			return;
		}

		boolean glowing = color != null;
		if (entity == null) {
			glowing = false;
		}
		if (entity instanceof OfflinePlayer) {
			if (!((OfflinePlayer) entity).isOnline()) {
				glowing = false;
			}
		}

		boolean wasGlowing = dataMap.containsKey(entity != null ? entity.getUniqueId() : null);
		GlowData glowData;
		if (wasGlowing && entity != null) {
			glowData = dataMap.get(entity.getUniqueId());
		} else {
			glowData = new GlowData();
		}

		Color oldColor = wasGlowing ? glowData.colorMap.get(receiver.getUniqueId()) : null;

		if (glowing) {
			glowData.colorMap.put(receiver.getUniqueId(), color);
		} else {
			glowData.colorMap.remove(receiver.getUniqueId());
		}
		if (glowData.colorMap.isEmpty()) {
			dataMap.remove(entity != null ? entity.getUniqueId() : null);
		} else {
			if (entity != null) {
				dataMap.put(entity.getUniqueId(), glowData);
			}
		}

		if (color != null && oldColor == color) {
			return;
		}
		if (entity == null) {
			return;
		}
		if (entity instanceof OfflinePlayer) {
			if (!((OfflinePlayer) entity).isOnline()) {
				return;
			}
		}
		if (!receiver.isOnline()) {
			return;
		}

		sendGlowPacket(entity, wasGlowing, glowing, receiver);
		if (oldColor != null && oldColor != Color.NONE/* We never add to NONE, so no need to remove */) {
			sendTeamPacket(entity, oldColor/* use the old color to remove the player from its team */, false, false,
					tagVisibility, push, receiver);
		}
		if (glowing) {
			sendTeamPacket(entity, color, false, color != Color.NONE, tagVisibility, push, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity
	 *            {@link Entity} to update
	 * @param color
	 *            {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or
	 *            <code>null</code> to stop glowing
	 * @param receiver
	 *            {@link Player} that will see the update
	 */
	public static void setGlowing(Entity entity, Color color, Player receiver) {
		setGlowing(entity, color, "always", "always", receiver);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity
	 *            {@link Entity} to update
	 * @param glowing
	 *            whether the entity is glowing or not
	 * @param receiver
	 *            {@link Player} that will see the update
	 * @see #setGlowing(Entity, Color, Player)
	 */
	public static void setGlowing(Entity entity, boolean glowing, Player receiver) {
		setGlowing(entity, glowing ? Color.NONE : null, receiver);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity
	 *            {@link Entity} to update
	 * @param glowing
	 *            whether the entity is glowing or not
	 * @param receivers
	 *            Collection of {@link Player}s that will see the update
	 * @see #setGlowing(Entity, Color, Player)
	 */
	public static void setGlowing(Entity entity, boolean glowing, Collection<? extends Player> receivers) {
		for (Player receiver : receivers) {
			setGlowing(entity, glowing, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity
	 *            {@link Entity} to update
	 * @param color
	 *            {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or
	 *            <code>null</code> to stop glowing
	 * @param receivers
	 *            Collection of {@link Player}s that will see the update
	 */
	public static void setGlowing(Entity entity, Color color, Collection<? extends Player> receivers) {
		for (Player receiver : receivers) {
			setGlowing(entity, color, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities
	 *            Collection of {@link Entity} to update
	 * @param color
	 *            {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or
	 *            <code>null</code> to stop glowing
	 * @param receiver
	 *            {@link Player} that will see the update
	 */
	public static void setGlowing(Collection<? extends Entity> entities, Color color, Player receiver) {
		for (Entity entity : entities) {
			setGlowing(entity, color, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities
	 *            Collection of {@link Entity} to update
	 * @param color
	 *            {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or
	 *            <code>null</code> to stop glowing
	 * @param receivers
	 *            Collection of {@link Player}s that will see the update
	 */
	public static void setGlowing(Collection<? extends Entity> entities, Color color,
			Collection<? extends Player> receivers) {
		for (Entity entity : entities) {
			setGlowing(entity, color, receivers);
		}
	}

	/**
	 * Check if an entity is glowing
	 *
	 * @param entity
	 *            {@link Entity} to check
	 * @param receiver
	 *            {@link Player} receiver to check (as used in the setGlowing
	 *            methods)
	 * @return <code>true</code> if the entity appears glowing to the player
	 */
	public static boolean isGlowing(Entity entity, Player receiver) {
		return getGlowColor(entity, receiver) != null;
	}

	/**
	 * Checks if an entity is glowing
	 *
	 * @param entity
	 *            {@link Entity} to check
	 * @param receivers
	 *            Collection of {@link Player} receivers to check
	 * @param checkAll
	 *            if <code>true</code>, this only returns <code>true</code> if the
	 *            entity is glowing for all receivers; if <code>false</code> this
	 *            returns <code>true</code> if the entity is glowing for any of the
	 *            receivers
	 * @return <code>true</code> if the entity appears glowing to the players
	 */
	public static boolean isGlowing(Entity entity, Collection<? extends Player> receivers, boolean checkAll) {
		if (checkAll) {
			boolean glowing = true;
			for (Player receiver : receivers) {
				if (!isGlowing(entity, receiver)) {
					glowing = false;
				}
			}
			return glowing;
		} else {
			for (Player receiver : receivers) {
				if (isGlowing(entity, receiver)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Get the glow-color of an entity
	 *
	 * @param entity
	 *            {@link Entity} to get the color for
	 * @param receiver
	 *            {@link Player} receiver of the color (as used in the setGlowing
	 *            methods)
	 * @return the {@link org.inventivetalent.glow.GlowAPI.Color}, or
	 *         <code>null</code> if the entity doesn't appear glowing to the player
	 */
	public static Color getGlowColor(Entity entity, Player receiver) {
		if (!dataMap.containsKey(entity.getUniqueId())) {
			return null;
		}
		GlowData data = dataMap.get(entity.getUniqueId());
		return data.colorMap.get(receiver.getUniqueId());
	}

	protected static void sendGlowPacket(Entity entity, boolean wasGlowing, boolean glowing, Player receiver) {
		try {
			if (PacketPlayOutEntityMetadata == null) {
				PacketPlayOutEntityMetadata = NMS_CLASS_RESOLVER.resolve("PacketPlayOutEntityMetadata");
			}
			if (DataWatcher == null) {
				DataWatcher = NMS_CLASS_RESOLVER.resolve("DataWatcher");
			}
			if (DataWatcherItem == null) {
				DataWatcherItem = NMS_CLASS_RESOLVER.resolve("DataWatcher$Item");
			}
			if (Entity == null) {
				Entity = NMS_CLASS_RESOLVER.resolve("Entity");
			}
			if (PacketPlayOutMetadataFieldResolver == null) {
				PacketPlayOutMetadataFieldResolver = new FieldResolver(PacketPlayOutEntityMetadata);
			}
			if (DataWatcherItemConstructorResolver == null) {
				DataWatcherItemConstructorResolver = new ConstructorResolver(DataWatcherItem);
			}
			if (EntityFieldResolver == null) {
				EntityFieldResolver = new FieldResolver(Entity);
			}
			if (DataWatcherMethodResolver == null) {
				DataWatcherMethodResolver = new MethodResolver(DataWatcher);
			}
			if (DataWatcherItemMethodResolver == null) {
				DataWatcherItemMethodResolver = new MethodResolver(DataWatcherItem);
			}
			if (EntityMethodResolver == null) {
				EntityMethodResolver = new MethodResolver(Entity);
			}
			if (DataWatcherFieldResolver == null) {
				DataWatcherFieldResolver = new FieldResolver(DataWatcher);
			}

			List list = new ArrayList();

			// Existing values
			Object dataWatcher = EntityMethodResolver.resolve("getDataWatcher").invoke(Minecraft.getHandle(entity));
			Map<Integer, Object> dataWatcherItems = (Map<Integer, Object>) DataWatcherFieldResolver
					.resolveByLastType(Map.class).get(dataWatcher);

			// Object dataWatcherObject =
			// EntityFieldResolver.resolve("ax").get(null);//Byte-DataWatcherObject
			Object dataWatcherObject = org.inventivetalent.packetlistener.reflection.minecraft.DataWatcher.V1_9.ValueType.ENTITY_FLAG
					.getType();

			byte prev = (byte) (dataWatcherItems.isEmpty() ? 0
					: DataWatcherItemMethodResolver.resolve("b").invoke(dataWatcherItems.get(0)));
			byte b = (byte) (glowing ? (prev | 1 << 6) : (prev & ~(1 << 6)));// 6 = glowing index
			Object dataWatcherItem = DataWatcherItemConstructorResolver.resolveFirstConstructor()
					.newInstance(dataWatcherObject, b);

			// The glowing item
			list.add(dataWatcherItem);

			Object packetMetadata = PacketPlayOutEntityMetadata.newInstance();
			PacketPlayOutMetadataFieldResolver.resolve("a").set(packetMetadata, -entity.getEntityId());// Use the
																										// negative ID
																										// so we can
																										// identify our
																										// own packet
			PacketPlayOutMetadataFieldResolver.resolve("b").set(packetMetadata, list);

			sendPacket(packetMetadata, receiver);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param receiver
	 *            {@link Player} receiver
	 * @param tagVisibility
	 *            visibility of the name-tag (always, hideForOtherTeams,
	 *            hideForOwnTeam, never)
	 * @param push
	 *            push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 */
	public static void initTeam(Player receiver, String tagVisibility, String push) {
		for (GlowAPI.Color color : GlowAPI.Color.values()) {
			GlowAPI.sendTeamPacket(null, color, true, false, tagVisibility, push, receiver);
		}
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param receiver
	 *            {@link Player} receiver
	 */
	public static void initTeam(Player receiver) {
		initTeam(receiver, TEAM_TAG_VISIBILITY, TEAM_PUSH);
	}

	protected static void sendTeamPacket(Entity entity, Color color,
			boolean createNewTeam/* If true, we don't add any entities */,
			boolean addEntity/* true->add the entity, false->remove the entity */, String tagVisibility, String push,
			Player receiver) {
		try {
			if (PacketPlayOutScoreboardTeam == null) {
				PacketPlayOutScoreboardTeam = NMS_CLASS_RESOLVER.resolve("PacketPlayOutScoreboardTeam");
			}
			if (PacketScoreboardTeamFieldResolver == null) {
				PacketScoreboardTeamFieldResolver = new FieldResolver(PacketPlayOutScoreboardTeam);
			}

			Object packetScoreboardTeam = PacketPlayOutScoreboardTeam.newInstance();
			PacketScoreboardTeamFieldResolver.resolve("i").set(packetScoreboardTeam,
					createNewTeam ? 0 : addEntity ? 3 : 4);// Mode (0 = create, 3 = add entity, 4 = remove entity)
			PacketScoreboardTeamFieldResolver.resolve("a").set(packetScoreboardTeam, color.getTeamName());// Name
			PacketScoreboardTeamFieldResolver.resolve("e").set(packetScoreboardTeam, tagVisibility);// NameTag
																									// visibility
			PacketScoreboardTeamFieldResolver.resolve("f").set(packetScoreboardTeam, push);// Team-push

			if (createNewTeam) {
				PacketScoreboardTeamFieldResolver.resolve("g").set(packetScoreboardTeam, color.packetValue);// Color ->
																											// this is
																											// what we
																											// care
																											// about
				PacketScoreboardTeamFieldResolver.resolve("c").set(packetScoreboardTeam, "§" + color.colorCode);// prefix
																												// - for
																												// some
																												// reason
																												// this
																												// controls
																												// the
																												// color,
																												// even
																												// though
																												// there's
																												// the
																												// extra
																												// color
																												// value...

				PacketScoreboardTeamFieldResolver.resolve("b").set(packetScoreboardTeam, color.getTeamName());// Display
																												// name
				PacketScoreboardTeamFieldResolver.resolve("d").set(packetScoreboardTeam, "");// suffix
				PacketScoreboardTeamFieldResolver.resolve("j").set(packetScoreboardTeam, 0);// Options - let's just
																							// ignore them for now
			}

			if (!createNewTeam) {
				// Add/remove players
				Collection<String> collection = ((Collection<String>) PacketScoreboardTeamFieldResolver.resolve("h")
						.get(packetScoreboardTeam));
				if (entity instanceof OfflinePlayer) {// Players still use the name...
					collection.add(entity.getName());
				} else {
					collection.add(entity.getUniqueId().toString());
				}
			}

			sendPacket(packetScoreboardTeam, receiver);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	protected static void sendPacket(Object packet, Player p) throws IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException, NoSuchFieldException, NoSuchMethodException {
		if (EntityPlayerFieldResolver == null) {
			EntityPlayerFieldResolver = new FieldResolver(NMS_CLASS_RESOLVER.resolve("EntityPlayer"));
		}
		if (PlayerConnectionMethodResolver == null) {
			PlayerConnectionMethodResolver = new MethodResolver(NMS_CLASS_RESOLVER.resolve("PlayerConnection"));
		}

		try {
			Object handle = Minecraft.getHandle(p);
			final Object connection = EntityPlayerFieldResolver.resolve("playerConnection").get(handle);
			PlayerConnectionMethodResolver.resolve("sendPacket").invoke(connection, packet);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Team Colors
	 */
	public enum Color {

		BLACK(0, "0"), DARK_BLUE(1, "1"), DARK_GREEN(2, "2"), DARK_AQUA(3, "3"), DARK_RED(4, "4"), DARK_PURPLE(5,
				"5"), GOLD(6, "6"), GRAY(7, "7"), DARK_GRAY(8, "8"), BLUE(9, "9"), GREEN(10, "a"), AQUA(11,
						"b"), RED(12, "c"), PURPLE(13, "d"), YELLOW(14, "e"), WHITE(15, "f"), NONE(-1, "");

		int packetValue;
		String colorCode;

		Color(int packetValue, String colorCode) {
			this.packetValue = packetValue;
			this.colorCode = colorCode;
		}

		String getTeamName() {
			String name = String.format("GAPI#%s", name());
			if (name.length() > 16) {
				name = name.substring(0, 16);
			}
			return name;
		}
	}

	public Friends friends;

	// This gets called either by #registerAPI above, or by the API manager if
	// another plugin requires this API
	@Override
	public void load() {
		// Require PacketListenerAPI
	}

	// This gets called either by #initAPI above or #initAPI in one of the requiring
	// plugins
	@Override
	public void init(Plugin plugin) {
		// Initialize other APIs we need

		// Register our events
		APIManager.registerEvents(this, this);

		PacketHandler.addHandler(new PacketHandler(plugin) {
			@Override
			@PacketOptions(forcePlayer = true)
			public void onSend(SentPacket sentPacket) {
				if ("PacketPlayOutEntityMetadata".equals(sentPacket.getPacketName())) {
					int a = (int) sentPacket.getPacketValue("a");
					if (a < 0) {// Our packet
						// Reset the ID and let it through
						sentPacket.setPacketValue("a", -a);
						return;
					}

					List b = (List) sentPacket.getPacketValue("b");
					if (b == null || b.isEmpty()) {
						return;// Nothing to modify
					}

					Entity entity = getEntityById(sentPacket.getPlayer().getWorld(), a);
					if (entity != null) {
						// Check if the entity is glowing
						if (GlowAPI.isGlowing(entity, sentPacket.getPlayer())) {
							if (GlowAPI.DataWatcherItemMethodResolver == null) {
								GlowAPI.DataWatcherItemMethodResolver = new MethodResolver(GlowAPI.DataWatcherItem);
							}
							if (GlowAPI.DataWatcherItemFieldResolver == null) {
								GlowAPI.DataWatcherItemFieldResolver = new FieldResolver(GlowAPI.DataWatcherItem);
							}

							try {
								// Update the DataWatcher Item
								// Object prevItem = b.get(0);
								for (Object prevItem : b) {
									Object prevObj = GlowAPI.DataWatcherItemMethodResolver.resolve("b")
											.invoke(prevItem);
									if (prevObj instanceof Byte) {
										byte prev = (byte) prevObj;
										byte bte = (byte) (true/* Maybe use the isGlowing result */ ? (prev | 1 << 6)
												: (prev & ~(1 << 6)));// 6 = glowing index
										GlowAPI.DataWatcherItemFieldResolver.resolve("b").set(prevItem, bte);
									}
								}
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						}
					}
				}
			}

			@Override
			public void onReceive(ReceivedPacket receivedPacket) {
			}
		});
	}

	@Override
	public void disable(Plugin plugin) {
	}

	@EventHandler
	public void onJoin(final PlayerJoinEvent event) {
		// Initialize the teams
		GlowAPI.initTeam(event.getPlayer());
	}

	@EventHandler
	public void onQuit(final PlayerQuitEvent event) {
		for (Player receiver : Bukkit.getOnlinePlayers()) {
			if (GlowAPI.isGlowing(event.getPlayer(), receiver)) {
				GlowAPI.setGlowing(event.getPlayer(), null, receiver);
			}
		}
	}

	protected static NMSClassResolver nmsClassResolver = new NMSClassResolver();
	protected static OBCClassResolver obcClassResolver = new OBCClassResolver();

	private static FieldResolver CraftWorldFieldResolver;
	private static FieldResolver WorldFieldResolver;
	private static MethodResolver IntHashMapMethodResolver;

	public static Entity getEntityById(World world, int entityId) {
		try {
			if (CraftWorldFieldResolver == null) {
				CraftWorldFieldResolver = new FieldResolver(obcClassResolver.resolve("CraftWorld"));
			}
			if (WorldFieldResolver == null) {
				WorldFieldResolver = new FieldResolver(nmsClassResolver.resolve("World"));
			}
			if (IntHashMapMethodResolver == null) {
				IntHashMapMethodResolver = new MethodResolver(nmsClassResolver.resolve("IntHashMap"));
			}
			if (EntityMethodResolver == null) {
				EntityMethodResolver = new MethodResolver(nmsClassResolver.resolve("Entity"));
			}

			Object entitiesById = WorldFieldResolver.resolve("entitiesById")
					.get(CraftWorldFieldResolver.resolve("world").get(world));
			Object entity = IntHashMapMethodResolver.resolve(new ResolverQuery("get", int.class)).invoke(entitiesById,
					entityId);
			if (entity == null) {
				return null;
			}
			return (Entity) EntityMethodResolver.resolve("getBukkitEntity").invoke(entity);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}