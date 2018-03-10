package org.jenkinsci.plugins.ansible_tower.exceptions;

/*
    Just our own type of exception
 */

public class AnsibleTowerDoesNotSupportAuthtoken extends AnsibleTowerException {
    public AnsibleTowerDoesNotSupportAuthtoken(String message) {
        super(message);
    }
}
