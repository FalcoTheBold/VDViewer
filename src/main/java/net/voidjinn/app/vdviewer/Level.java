/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.voidjinn.app.vdviewer;

/**
 *
 * @author CibonTerra
 */
public class Level {

    private final String name;
    private final String assetPath;

    public Level(String name, String assetPath) {
        this.name = name;
        this.assetPath = assetPath;
    }

    public String getName() {
        return name;
    }

    public String getAssetPath() {
        return assetPath;
    }

    public enum FileType {
        UNKNOWN(""),
        OBJ("obj"),
        GLTF("gltf");

        private final String format;

        private FileType(String format) {
            this.format = format;
        }

        public static FileType lookValidFormat(String format) {
            for (FileType value : values()) {
                if (format.equals(value.getFormat())) {
                    return value;
                }
            }
            return UNKNOWN;
        }

        public String getFormat() {
            return format;
        }

    }

}
