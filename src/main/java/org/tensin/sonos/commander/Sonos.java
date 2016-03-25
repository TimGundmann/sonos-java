package org.tensin.sonos.commander;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.message.header.UDAServiceTypeHeader;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;
import org.tensin.sonos.SonosConstants;
import org.tensin.sonos.SonosException;
import org.tensin.sonos.control.ZonePlayer;
import org.tensin.sonos.gen.*;
import org.tensin.sonos.gen.AVTransport.GetPositionInfoResponse;
import org.tensin.sonos.gen.AVTransport.TransportState;
import org.tensin.sonos.helpers.EntryHelper;
import org.tensin.sonos.helpers.RemoteDeviceHelper;
import org.tensin.sonos.helpers.TimeUtilities;
import org.tensin.sonos.model.*;
import org.tensin.sonos.xml.ResultParser;
import org.tensin.sonos.xml.ZoneGroupStateHandler;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 */
public class Sonos implements Closeable {
	private static final Logger log = LoggerFactory.getLogger(Sonos.class);

	private static final int DEFAULT_UDP_SEARCH_TIME = 120;
	public static final int MAX_TRIES = 10;

	private List<ZonePlayer> zonePlayers = Lists.newArrayList();

	private UpnpService upnpService;

	private int udpSearchTime = DEFAULT_UDP_SEARCH_TIME;

	public Sonos(long disoveryMillis) {
		startDiscovery();
		TimeUtilities.waitMilliSeconds(disoveryMillis);
	}

	public Sonos() {
		this(SonosConstants.MAX_DISCOVER_TIME_IN_MILLISECONDS);
	}
	
	private ZonePlayer addZonePlayer(RemoteDevice dev) {
		synchronized (zonePlayers) {
			if (isZonePlayerAlreadyDefined(dev.getIdentity().getUdn().getIdentifierString())) {
				return null;
			}

			// Ignore zone bridges
			// TODO may need to implement cut down zone player for the zone
			// bridge
			// I believe the bridge only supports the following interfaces:
			// - DeviceProperties
			// - GroupManagement
			// - SystemProperties
			// - ZoneGroup
			Collection<String> ignoredDevicesModelName = new ArrayList<String>();
			// ignoredDevicesModelName.add("ZB100");
			// ignoredDevicesModelName.add("BR100");
			for (String ignoredDeviceModelName : ignoredDevicesModelName) {
				if (dev.getDetails().getModelDetails().getModelNumber().toUpperCase().contains(ignoredDeviceModelName))
					return null;
			}

			log.info("Adding zone: " + getDeviceDescription(dev));

			try {
				ZonePlayer zone = new ZonePlayer(upnpService, dev);
				zonePlayers.add(zone);
				return zone;
			} catch (Exception e) {
				log.error("Couldn't add zone" + getDeviceDescription(dev), e);
			}

			return null;
		}
	}

	private String getDeviceDescription(RemoteDevice dev) {
		return dev.getType().getDisplayString() + " " + dev.getDetails().getModelDetails().getModelDescription() + " "
				+ dev.getDetails().getModelDetails().getModelName() + " "
				+ dev.getDetails().getModelDetails().getModelNumber();
	}

	public ZonePlayer getPlayer(String name) {
		name = name.toLowerCase();
		synchronized (zonePlayers) {
			for (ZonePlayer zp : zonePlayers) {
				if (getZoneName(zp).toLowerCase().equals(name))
					return zp;
			}
			throw new RuntimeException("Invalid player id: [" + name + "]");
		}
	}

	private ZonePlayer getZonePlayerById(String id) {
		synchronized (zonePlayers) {
			for (ZonePlayer zp : zonePlayers) {
				if (zp.getId().equals(id))
					return zp;
			}
			throw new RuntimeException("Invalid player id: [" + id + "]");
		}
	}

	private ZonePlayer getZonePlayerByUDN(String udn) {
		for (ZonePlayer zone : zonePlayers) {
			if (zone.getRootDevice().getIdentity().getUdn().getIdentifierString().equals(udn))
				return zone;
		}
		return null;
	}

	private boolean isZonePlayerAlreadyDefined(String udn) {
		return getZonePlayerByUDN(udn) != null;
	}

	private void removeZonePlayer(String udn) {
		synchronized (zonePlayers) {
			ZonePlayer zp = getZonePlayerByUDN(udn);
			if (zp != null) {
				log.info("Removing ZonePlayer " + udn + " "
						+ zp.getRootDevice().getDetails().getModelDetails().getModelDescription());
				zonePlayers.remove(zp);
				zp.dispose();
			}
		}
	}

	public void close() {
		log.info("Shutting down UPNP services and discovery");
		upnpService.shutdown();
		log.info("Cleaning up internal resources");
		synchronized (zonePlayers) {
			for (ZonePlayer zp : zonePlayers)
				zp.dispose();
		}
	}

	protected UpnpService createUpnpService(RegistryListener listener) {
		return new UpnpServiceImpl(listener);
	}

	private void startDiscovery() {
		upnpService = createUpnpService(listener);
		// Send a search message to all devices and services, they should
		// respond soon
		UDAServiceType udaType = new UDAServiceType(SonosConstants.AV_TRANSPORT);
		upnpService.getControlPoint().search(new UDAServiceTypeHeader(udaType), udpSearchTime);
	}

	private boolean isSonosDevice(RemoteDevice device) {
		return device.getDetails().getManufacturerDetails().getManufacturer().toUpperCase().contains("SONOS");
	}

	/**
	 * The listener.
	 */
	private RegistryListener listener = new DefaultRegistryListener() {
		@Override
		public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
			if (isSonosDevice(device))
				addZonePlayer(device);
		}

		@Override
		public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
			if (isSonosDevice(device)) {
				String udn = device.getIdentity().getUdn().toString();
				removeZonePlayer(udn);
				log.info("Device [" + udn + "] disconnected");
			}
		}
	};

	private ZonePlayer getCoordinator(ZonePlayer zp) {
		if (zp == null)
			return null;

		ZoneGroupTopology.GetZoneGroupStateResponse response = getZoneGroupTopology(zp).getZoneGroupState().execute();
		ZoneGroupState state = ResultParser.getGroupStateFromResult(response.zoneGroupState());
		if (state == null)
			return zp;
		for (final ZoneGroup zg : state.getGroups()) {
			if (zg.getMembers().contains(zp.getId()))
				return getZonePlayerById(zg.getCoordinator());
		}
		return zp;
	}

	public boolean isPlaying(ZonePlayer player) {
		return getAvTransport(player).getTransportInfo().execute().currentTransportState().equals(TransportState.PLAYING);
	}

	public List<String> getZoneNames() {
		List<String> zones = Lists.newArrayList();
		for (ZonePlayer player : zonePlayers)
			zones.add(getZoneName(player));
		return zones;
	}

	public String getInfo(ZonePlayer player) {
		return RemoteDeviceHelper.dumpRemoteDevice(player.getRootDevice());
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Facade

	public AVTransport.AddURIToQueueResponse enqueue(ZonePlayer player, String url) {
		Entry entry = EntryHelper.createEntryForUrl(url);
		return getAvTransport(getCoordinator(player)).addURIToQueue().enqueuedURI(entry.getRes())
				.enqueuedURIMetaData(EntryHelper.compileMetadataString(entry)).enqueueAsNext(true).execute();
	}

	public Iterable<Entry> browse(final ZonePlayer player, String type) {
		// TODO paging support
		ContentDirectory.BrowseResponse response = getContentDirectory(player).browse().objectID(type).filter("*")// "dc:title,res,dc:creator,upnp:artist,upnp:album")
				.browseFlag(ContentDirectory.BrowseFlag.BrowseDirectChildren).requestedCount(Integer.MAX_VALUE)
				.execute();
		String xml = response.result();
		return ResultParser.getEntriesFromStringResult(xml);
	}

	public Iterable<Entry> browseArtists(final ZonePlayer player) {
		return browse(player, "A:ARTIST");
	}

	public void crossFade(ZonePlayer player, boolean crossfade) {
		getAvTransport(player).setCrossfadeMode().crossfadeMode(crossfade).execute();
	}

	public String getZoneName(ZonePlayer player) {
		return getDeviceProperties(player).getZoneAttributes().execute().currentZoneName();
	}

	public void lineIn(ZonePlayer player) {
		getAudioIn(player).selectAudio().objectID("urn:schemas-upnp-org:service:AudioIn:1");
	}

	public void moveTracks(ZonePlayer player, int startAt, int count, int insertBefore) {
		getAvTransport(player).reorderTracksInQueue().startingIndex(startAt).numberOfTracks(count)
				.insertBefore(insertBefore).execute();
	}

	public void mute(ZonePlayer player, boolean mute) {
		getRenderingControl(player).setMute().desiredMute(mute).execute();
	}

	public void next(ZonePlayer player) {
		getAvTransport(player).next().execute();
	}

	public void previous(ZonePlayer player) {
		getAvTransport(player).previous().execute();
	}

	public void pause(ZonePlayer player) {
		getAvTransport(player).pause().execute();
	}

	public void play(ZonePlayer player) {
		getAvTransport(player).play().speed("1").execute();
	}

	public void play(ZonePlayer player, String url) {
		track(player, enqueue(player, url).firstTrackNumberEnqueued());
		play(player);
	}

	public void play(ZonePlayer player, int index) {
		track(player, index);
	}

	public int currentSongIndex(ZonePlayer player) {
		return getAvTransport(player)
				.getPositionInfo()
				.instanceID(0)
				.execute()
				.track();
	}
	
	public void remove(ZonePlayer player, String url) {
		Entry entry = EntryHelper.createEntryForUrl(url);
		getAvTransport(player)
			.removeTrackFromQueue()
			.objectID(entry.getId())
			.execute();
	}

	public void remove(ZonePlayer player, int index) {
		getAvTransport(player)
			.removeTrackRangeFromQueue()
			.startingIndex(index)
			.numberOfTracks(1)
			.execute();
	}

	public void clearQueue(ZonePlayer player) {
		getAvTransport(player).removeAllTracksFromQueue().execute();
	}

	public void saveQueue(ZonePlayer player, String title) {
		getAvTransport(player).saveQueue().title(title).execute();
	}

	public void saveQueue(ZonePlayer player, String title, String playlistId) {
		getAvTransport(player).saveQueue().title(title).objectID(playlistId);
	}

	public void shuffle(ZonePlayer player, boolean shuffle) {
		getAvTransport(player).setPlayMode()
				.newPlayMode(
						shuffle ? AVTransport.CurrentPlayMode.SHUFFLE_NOREPEAT : AVTransport.CurrentPlayMode.NORMAL)
				.execute();
	}

	public void track(ZonePlayer player, int track) {
		getAvTransport(player).seek().unit(AVTransport.SeekMode.TRACK_NR).target("" + track).execute();
	}
	
	public void forward(ZonePlayer player, String realTime) {
		getAvTransport(player).seek().unit(AVTransport.SeekMode.REL_TIME).target(realTime).execute();
	}

	public int volume(ZonePlayer player) {
		return getRenderingControl(player).getVolume().channel(RenderingControl.Channel.Master).execute()
				.currentVolume();
	}

	public void setVolume(ZonePlayer player, int volume) {
		volume = clampVolume(volume);
		// Seem to be unreliable, so we set and verify as many times as needed
		int tries = MAX_TRIES;
		while (tries-- != 0) {
			getRenderingControl(player).setVolume().desiredVolume(volume).channel(RenderingControl.Channel.Master)
					.execute();
			if (volume(player) == volume)
				return;
		}
		log.warn("Failed to set volume to: " + volume + " in zone " + getZoneName(player));
	}

	private int clampVolume(int volume) {
		volume = Math.max(0, Math.min(100, volume));
		return volume;
	}

	public void adjustVolume(ZonePlayer player, int volumeChange) {
		setVolume(player, volume(player) + volumeChange);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Raw service wrappers

	private <T> T getService(Class<T> serviceClass, ZonePlayer player) {
		try {
			return serviceClass.getConstructor(UpnpService.class, RemoteDevice.class).newInstance(upnpService,
					player.getRootDevice());
		} catch (Throwable e) {
			throw new SonosException(e);
		}
	}

	public AVTransport getAvTransport(ZonePlayer player) {
		return getService(AVTransport.class, player);
	}

	public ContentDirectory getContentDirectory(ZonePlayer player) {
		return getService(ContentDirectory.class, player);
	}

	public DeviceProperties getDeviceProperties(ZonePlayer player) {
		return getService(DeviceProperties.class, player);
	}

	public AlarmClock getAlarmClock(ZonePlayer player) {
		return getService(AlarmClock.class, player);
	}

	public AudioIn getAudioIn(ZonePlayer player) {
		return getService(AudioIn.class, player);
	}

	public ConnectionManager getConnectionManager(ZonePlayer player) {
		return getService(ConnectionManager.class, player);
	}

	public GroupManagement getGroupManagement(ZonePlayer player) {
		return getService(GroupManagement.class, player);
	}

	public GroupRenderingControl getGroupRenderingControl(ZonePlayer player) {
		return getService(GroupRenderingControl.class, player);
	}

	public MusicServices getMusicServices(ZonePlayer player) {
		return getService(MusicServices.class, player);
	}

	public QPlay getQPlay(ZonePlayer player) {
		return getService(QPlay.class, player);
	}

	public Queue getQueue(ZonePlayer player) {
		return getService(Queue.class, player);
	}

	public RenderingControl getRenderingControl(ZonePlayer player) {
		return getService(RenderingControl.class, player);
	}

	public SystemProperties getSystemProperties(ZonePlayer player) {
		return getService(SystemProperties.class, player);
	}

	public ZoneGroupTopology getZoneGroupTopology(ZonePlayer player) {
		return getService(ZoneGroupTopology.class, player);
	}

	public GetPositionInfoResponse currentSongPosition(ZonePlayer player) {
		return getAvTransport(player)
				.getPositionInfo()
				.execute();
	}

}
