package org.jenkinsci.plugins.ansible_tower;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import org.jenkinsci.plugins.ansible_tower.util.TowerConnector;
import org.jenkinsci.plugins.ansible_tower.util.TowerInstallation;

import java.io.PrintStream;

public class AnsibleTowerRunner {
    public boolean runJobTemplate(
            PrintStream logger, String towerServer, String jobTemplate, String extraVars, String limit,
            String jobTags, String inventory, String credential, boolean verbose, boolean importTowerLogs,
            boolean removeColor, EnvVars envVars
    ) {
        if(verbose) { logger.println("Beginning Ansible Tower Run on "+ towerServer); }

        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if(towerConfigToRunOn == null) {
            logger.println("ERROR: Ansible tower server "+ towerServer +" does not exist in Ansible Tower configuration");
            return false;
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();

        // Expand all of the parameters
        String expandedJobTemplate = envVars.expand(jobTemplate);
        String expandedExtraVars = envVars.expand(extraVars);
        String expandedLimit = envVars.expand(limit);
        String expandedJobTags = envVars.expand(jobTags);
        String expandedInventory = envVars.expand(inventory);
        String expandedCredential = envVars.expand(credential);

        if(verbose) {
            if(!expandedJobTemplate.equals(jobTemplate)) { logger.println("Expanded job template to "+ expandedJobTemplate); }
            if(!expandedExtraVars.equals(extraVars))     { logger.println("Expanded extra vars to "+ expandedExtraVars); }
            if(!expandedLimit.equals(limit))             { logger.println("Expanded limit to "+ expandedLimit); }
            if(!expandedJobTags.equals(jobTags))         { logger.println("Expanded job tags to "+ expandedJobTags); }
            if(!expandedInventory.equals(inventory))     { logger.println("Expanded inventory to "+ expandedInventory); }
            if(!expandedCredential.equals(credential))   { logger.println("Expanded credentials to "+ expandedCredential); }
        }

        if(verbose) { logger.println("Requesting tower to run job template "+ jobTemplate); }
        int myJobID;
        try {
            myJobID = myTowerConnection.submitJob(expandedJobTemplate, expandedExtraVars, expandedLimit, expandedJobTags, expandedInventory, expandedCredential);
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Unable to request job template invocation "+ e.getMessage());
            return false;
        }

        logger.println("Job URL: "+ towerConfigToRunOn.getTowerURL() +"/#/jobs/"+ myJobID);

        boolean jobCompleted = false;
        while(!jobCompleted) {
            // First log any events if the user wants them
            try {
                if (importTowerLogs) { myTowerConnection.logJobEvents(myJobID, logger, removeColor); }
            } catch(AnsibleTowerException e) {
                logger.println("ERROR: Faile to get job events from tower: "+ e.getMessage());
                return false;
            }
            try {
                jobCompleted = myTowerConnection.isJobCommpleted(myJobID);
            } catch(AnsibleTowerException e) {
                logger.println("ERROR: Failed to get job status from Tower: "+ e.getMessage());
                return false;
            }
            if(!jobCompleted) {
                try {
                    Thread.sleep(3000);
                } catch(InterruptedException ie) {
                    logger.println("ERROR: Got interrupted while sleeping");
                    return false;
                }
            }
        }

        try {
            if(myTowerConnection.isJobFailed(myJobID)) {
                logger.println("Tower failed to complete the requeted job");
                return false;
            } else {
                if(verbose) { logger.println("Tower completed the requested job"); }
                return true;
            }
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Failed to job failure status from Tower: "+ e.getMessage());
            return false;
        }
    }
}
