package org.jenkinsci.plugins.ansible_tower;

/*
    This class is a bridge between the Jenkins workflow/plugin step and TowerConnector.
    The intention is to abstract the "work" from the two Jenkins classes
 */

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.jenkinsci.plugins.ansible_tower.util.TowerConnector;
import org.jenkinsci.plugins.ansible_tower.util.TowerInstallation;
import org.jenkinsci.plugins.envinject.service.EnvInjectActionSetter;

import java.io.PrintStream;
import java.util.HashMap;

public class AnsibleTowerRunner {
    public boolean runJobTemplate(
            PrintStream logger, String towerServer, String jobTemplate, String extraVars, String limit,
            String jobTags, String inventory, String credential, boolean verbose, boolean importTowerLogs,
            boolean removeColor, EnvVars envVars, String templateType, boolean importWorkflowChildLogs,
            FilePath ws, Run<?,?> run
    ) {
        if (verbose) {
            logger.println("Beginning Ansible Tower Run on " + towerServer);
        }

        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if (towerConfigToRunOn == null) {
            logger.println("ERROR: Ansible tower server " + towerServer + " does not exist in Ansible Tower configuration");
            return false;
        }

        if(templateType == null || (!templateType.equalsIgnoreCase(TowerConnector.WORKFLOW_TEMPLATE_TYPE) && !templateType.equalsIgnoreCase(TowerConnector.JOB_TEMPLATE_TYPE))) {
            logger.println("ERROR: Template type "+ templateType +" was invalid");
            return false;
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();

        // If they came in empty then set them to null so that we don't pass a nothing through
        if(jobTemplate.equals(""))  { jobTemplate = null; }
        if(extraVars.equals(""))    { extraVars= null; }
        if(limit.equals(""))        { limit= null; }
        if(jobTags.equals(""))      { jobTags= null; }
        if(inventory.equals(""))    { inventory= null; }
        if(credential.equals(""))   { credential= null; }

        // Expand all of the parameters
        String expandedJobTemplate = envVars.expand(jobTemplate);
        String expandedExtraVars = envVars.expand(extraVars);
        String expandedLimit = envVars.expand(limit);
        String expandedJobTags = envVars.expand(jobTags);
        String expandedInventory = envVars.expand(inventory);
        String expandedCredential = envVars.expand(credential);

        if (verbose) {
            if(expandedJobTemplate != null && !expandedJobTemplate.equals(jobTemplate)) {
                logger.println("Expanded job template to " + expandedJobTemplate);
            }
            if(expandedExtraVars != null && !expandedExtraVars.equals(extraVars)) {
                logger.println("Expanded extra vars to " + expandedExtraVars);
            }
            if(expandedLimit != null && !expandedLimit.equals(limit)) {
                logger.println("Expanded limit to " + expandedLimit);
            }
            if(expandedJobTags != null && !expandedJobTags.equals(jobTags)) {
                logger.println("Expanded job tags to " + expandedJobTags);
            }
            if(expandedInventory != null && !expandedInventory.equals(inventory)) {
                logger.println("Expanded inventory to " + expandedInventory);
            }
            if(expandedCredential != null && !expandedCredential.equals(credential)) {
                logger.println("Expanded credentials to " + expandedCredential);
            }
        }

        if(expandedJobTags != null && expandedJobTags.equalsIgnoreCase("")) {
            if(!expandedJobTags.startsWith(",")) {
                expandedJobTags = ","+ expandedJobTags;
            }
        }

        // Get the job template.
        JSONObject template = null;
        try {
            template = myTowerConnection.getJobTemplate(expandedJobTemplate, templateType);
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Unable to lookup job template " + e.getMessage());
            return false;
        }


        if(expandedExtraVars != null && template.containsKey("ask_variables_on_launch") && template.getBoolean("ask_variables_on_launch")) {
            logger.println("[WARNING]: Extra variables defined but prompt for variables on launch is not set in tower job");
        }
        if(expandedLimit != null && template.containsKey("ask_limit_on_launch") && template.getBoolean("ask_limit_on_launch")) {
            logger.println("[WARNING]: Limit defined but prompt for limit on launch is not set in tower job");
        }
        if(expandedJobTags != null && template.containsKey("ask_tags_on_launch") && template.getBoolean("ask_tags_on_launch")) {
            logger.println("[WARNING]: Job Tags defined but prompt for tags on launch is not set in tower job");
        }
        if(expandedInventory != null && template.containsKey("ask_inventory_on_launch") && template.getBoolean("ask_inventory_on_launch")) {
            logger.println("[WARNING]: Inventory defined but prompt for inventory on launch is not set in tower job");
        }
        if(expandedCredential != null && template.containsKey("ask_credential_on_launch") && template.getBoolean("ask_credential_on_launch")) {
            logger.println("[WARNING]: Credential defined but prompt for credential on launch is not set in tower job");
        }
        // Here are some more options we may want to use someday
        //    "ask_diff_mode_on_launch": false,
        //    "ask_skip_tags_on_launch": false,
        //    "ask_job_type_on_launch": false,
        //    "ask_verbosity_on_launch": false,


        if(verbose) {
            logger.println("Requesting tower to run " + templateType + " template " + expandedJobTemplate);
        }
        int myJobID;
        try {
            myJobID = myTowerConnection.submitTemplate(template.getInt("id"), expandedExtraVars, expandedLimit, expandedJobTags, expandedInventory, expandedCredential, templateType);
        } catch (AnsibleTowerException e) {
            logger.println("ERROR: Unable to request job template invocation " + e.getMessage());
            return false;
        }

        String jobURL = myTowerConnection.getJobURL(myJobID, templateType);

        logger.println("Template Job URL: "+ jobURL);

        myTowerConnection.setLogTowerEvents(importTowerLogs);
        myTowerConnection.setJenkinsLogger(logger);
        myTowerConnection.setRemoveColor(removeColor);
        boolean jobCompleted = false;
        while(!jobCompleted) {
            // First log any events if the user wants them
            try {
                myTowerConnection.logEvents(myJobID, templateType, importWorkflowChildLogs);
            } catch(AnsibleTowerException e) {
                logger.println("ERROR: Failed to get job events from tower: "+ e.getMessage());
                return false;
            }
            try {
                jobCompleted = myTowerConnection.isJobCommpleted(myJobID, templateType);
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

        HashMap<String, String> jenkinsVariables = myTowerConnection.getJenkinsExports();
        for(String key : jenkinsVariables.keySet()) {
            if(verbose) { logger.println("Recieveing from Jenkins job '"+ key +"' with value '"+ jenkinsVariables.get(key) +"'"); }
            envVars.put(key, jenkinsVariables.get(key));
        }
        if(envVars.size() != 0) {
            if(Jenkins.getInstance().getPlugin("envinject") == null) {
                logger.println("Found environment variables to inject but the EnvInject plugin was not found");
            } else {
                EnvInjectActionSetter envInjectActionSetter = new EnvInjectActionSetter(ws);
                try {
                    envInjectActionSetter.addEnvVarsToRun(run, envVars);
                } catch(Exception e) {
                    logger.println("Unable to inject environment variables: " + e.getMessage());
                    return false;
                }
            }
        }

        try {
            if(myTowerConnection.isJobFailed(myJobID, templateType)) {
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
