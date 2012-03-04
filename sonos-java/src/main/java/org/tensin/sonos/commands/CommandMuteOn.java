package org.tensin.sonos.commands;

import org.tensin.sonos.ISonos;
import org.tensin.sonos.upnp.SonosException;

public class CommandMuteOn extends AbstractCommand implements IZoneCommand {

    @Override
    public void execute(final ISonos sonos) throws SonosException {
        sonos.setMute(true);
    }

    @Override
    public String getName() {
        return "mute";
    }

}
