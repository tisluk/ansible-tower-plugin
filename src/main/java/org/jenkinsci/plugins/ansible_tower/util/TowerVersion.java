package org.jenkinsci.plugins.ansible_tower.util;

import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;

public class TowerVersion {
    private int major = 0;
    private int minor = 0;
    private int point = 0;
    private String version = "";

    public TowerVersion(String version) throws AnsibleTowerException {
        this.version = version;
        String[] parts = version.split("\\.");
        if(parts.length != 3) {
            System.out.println("Got "+ parts.length +" segments");
            throw new AnsibleTowerException("The version passed to TowerVersion must be in the format X.Y.Z");
        }
        try {
            this.major = Integer.parseInt(parts[0]);
        } catch(Exception e) {
            throw new AnsibleTowerException("The major version ("+ parts[0] +") could not be parsed as an int: "+ e.getMessage());
        }
        try {
            this.minor = Integer.parseInt(parts[1]);
        } catch(Exception e) {
            throw new AnsibleTowerException("The minor version ("+ parts[1] +") could not be parsed as an int: "+ e.getMessage());
        }
        try {
            this.point = Integer.parseInt(parts[2]);
        } catch(Exception e) {
            throw new AnsibleTowerException("The point version ("+ parts[2] +") could not be parsed as an int: "+ e.getMessage());
        }
    }

    public int getMajorVersion() { return this.major; }
    public int getMinorVersion() { return this.minor; }
    public int getPointVersion() { return this.point; }
    public String getVersion() { return version; }

    public boolean is_greater_or_equal(String anotherVersionString) throws AnsibleTowerException {
        TowerVersion anotherVersion = new TowerVersion(anotherVersionString);
        if(anotherVersion.getMajorVersion() < this.major) { return true; }
        if(anotherVersion.getMinorVersion() < this.minor) { return true; }
        if(anotherVersion.getMajorVersion() == this.major && anotherVersion.getMinorVersion() == this.minor && anotherVersion.getPointVersion() <= this.point) { return true; }
        return false;
    }
}
