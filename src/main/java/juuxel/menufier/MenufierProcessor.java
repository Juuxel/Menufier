/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.menufier;

import net.fabricmc.loom.processors.MappingProcessor;

import java.nio.file.Path;

/**
 * A mapping processor that converts screen handlers to menus.
 */
public final class MenufierProcessor implements MappingProcessor {
    @Override
    public void process(final Path mappings, final MappingType type) {
        final String mappingsPath = mappings.toAbsolutePath().toString();
        Menufier.main(mappingsPath, mappingsPath);
    }
}
