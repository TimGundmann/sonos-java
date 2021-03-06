package org.tensin.sonos;

import com.google.common.base.Strings;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensin.sonos.commander.Sonos;
import org.tensin.sonos.control.ZonePlayer;
import org.tensin.sonos.helpers.RemoteDeviceHelper;
import org.tensin.sonos.model.Entry;

import java.util.List;

/**
 * The Class CLITestCase.
 */
@Ignore
public class SonosTestCase {
    private static final Logger log = LoggerFactory.getLogger(SonosTestCase.class);

    public String zone1 = "Living Room";
    public String zone2 = "Kitchen";

    private Sonos sonos;

    @Before
    public void setUp() {
        sonos = new Sonos();
    }

    @After
    public void tearDown() {
        sonos.close();
    }

    @Test
    public void testDiscover() {
        List<String> zones = sonos.getZoneNames();
        log.info("Discovered: " + zones);
    }

    /**
     * Test list.
     * (1) id: A: / Attributes
     * (2) id: S: / Music Shares
     * (3) id: Q: / Queues
     * (4) id: SQ: / Saved Queues
     * (5) id: R: / Internet Radio
     * (6) id: EN: / Entire Network
     *
     * @throws org.tensin.sonos.SonosException
     *             the sonos exception
     */
    @Test
    public void testList() {
    	zone1 = sonos.getZoneNames().get(0);
        String[] types = {"A:", "S:", /*"Q",*/ "Q:0", "SQ:", /*"R:",*/ "EN:"};//, "S://server_smb/Sara", "A:PLAYLISTS", "A:TRACKS"};
        for (String type : types) {
            log.info("Browsing " + type);
            Iterable<Entry> entries = sonos.browse(sonos.getPlayer(zone1), type, "*");
            dumpEntries(entries);
        }
    }

    private void dumpEntries(Iterable<Entry> entries) {
        for (Entry entry : entries)
            log.info(dumpEntry(entry));
    }

    private String dumpEntry(Entry entry) {
        StringBuilder s = new StringBuilder();
        if (!Strings.isNullOrEmpty(entry.getId()))
            s.append("id: " + entry.getId() + "\n");
        if (!Strings.isNullOrEmpty(entry.getTitle()))
            s.append("title: " + entry.getTitle() + "\n");
        if (!Strings.isNullOrEmpty(entry.getParentId()))
            s.append("parentId: " + entry.getParentId() + "\n");
        if (!Strings.isNullOrEmpty(entry.getAlbum()))
            s.append("album: " + entry.getAlbum() + "\n");
        if (!Strings.isNullOrEmpty(entry.getAlbumArtist()))
            s.append("albumArtist: " + entry.getAlbumArtist() + "\n");
        if (!Strings.isNullOrEmpty(entry.getAlbumArtUri()))
            s.append("albumArtUri: " + entry.getAlbumArtUri() + "\n");
        if (!Strings.isNullOrEmpty(entry.getCreator()))
            s.append("creator: " + entry.getCreator() + "\n");
        if (!Strings.isNullOrEmpty(entry.getUpnpClass()))
            s.append("upnpClass: " + entry.getUpnpClass() + "\n");
        if (!Strings.isNullOrEmpty(entry.getRes()))
            s.append("res: " + entry.getRes() + "\n");
        if (entry.getOriginalTrackNumber() != 0)
            s.append("originalTrackNumber: " + entry.getOriginalTrackNumber() + "\n");
        return s.toString();
    }

    @Test
    public void testPlay()  {
        ZonePlayer player = sonos.getPlayer(zone1);
        dumpEntries(sonos.browse(player, "Q:0", "*"));
        sonos.clearQueue(player);
        sonos.enqueue(player, "cifs://server_smb/sonos/Classical/Bach, Johann Sebastian/Goldberg Variations/Glenn Gould - Bach  The Goldberg Variations (1955)/02 Variation 1 a 1 Clav..flac");
        dumpEntries(sonos.browse(player, "Q:0", "*"));
        sonos.play(player);
    }

    @Test
    public void testVolume() {
        ZonePlayer player = sonos.getPlayer(zone1);
        sonos.setVolume(player, 20);
        //log.info("" + sonos.volume(player));
        assert sonos.volume(player) == 20;
        sonos.setVolume(player, 10);
        assert sonos.volume(player) == 10;
        sonos.adjustVolume(player, 10);
        assert sonos.volume(player) == 20;
        sonos.adjustVolume(player, -10);
        assert sonos.volume(player) == 10;
    }

    @Test
    public void testDeviceDump() {
        log.info(RemoteDeviceHelper.dumpRemoteDevice(sonos.getPlayer(zone1).getRootDevice()));
    }

//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testMute() {
//        CLIController.main(new String[] { "--command", "mute", "--zone", ZONE1});
//
//        ZoneCommandExecutor executor = zoneCommandDispatcher.getZoneCommandExecutor(ZONE1);
//        Assert.assertTrue(executor != null);
//        Assert.assertEquals(1, executor.getExecutedCommandsCount());
//
//        executor = zoneCommandDispatcher.getZoneCommandExecutor(ZONE2);
//        Assert.assertTrue(executor != null);
//        Assert.assertEquals(0, executor.getExecutedCommandsCount());
//    }
//
//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testNext() {
//        CLIController.main(new String[] { "--command", "next", "--zone", ZONE2});
//    }
//
//    /**
//     * Test cli.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testPause() {
//        CLIController.main(new String[] { "--command", "pause", "--zone", "ALL" });
//    }
//
//    /**
//     * Test cli.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testPlay() {
//        CLIController.main(new String[] { "--command", "play", "--zone", ZONE1});
//    }
//
//    /**
//     * Test cli.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testUnknownCommand() {
//        try {
//            CLIController.main(new String[] { "--zzz" });
//        } catch (final ParameterException e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Test cli.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testUnknownOption() {
//        CLIController.main(new String[] { "--command", "dis" });
//    }
//
//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testUnknownZone() {
//        CLIController.main(new String[] { "--command", "mute", "--zone", "zzzzzzzzzz" });
//    }
//
//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testUnmute() {
//        CLIController.main(new String[] { "--command", "unmute", "--zone", ZONE1});
//    }
//
//    /**
//     * Test cli.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testUsage() {
//        CLIController.main(new String[] { "--usage" });
//    }
//
//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testVolume() {
//        CLIController.main(new String[] { "--command", "volume", "--zone", ZONE2});
//    }
//
//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testVolumeDown() {
//        CLIController.main(new String[] { "--command", "down", "--zone", ZONE2});
//    }
//
//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testVolumeSet() {
//        CLIController.main(new String[] { "--command", "volume", "25", "--zone", ZONE2});
//    }
//
//    /**
//     * Test next.
//     *
//     * @throws org.tensin.sonos.SonosException
//     *             the sonos exception
//     */
//    @Test
//    public void testVolumeUp() {
//        CLIController.main(new String[] { "--command", "up", "--zone", ZONE2});
//    }

}
