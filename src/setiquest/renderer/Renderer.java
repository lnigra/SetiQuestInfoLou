/*******************************************************************************

File:    Renderer.java
Project: SetiQuestInfo
Authors: Jon Richards - The SETI Institute

Copyright 2012 The SETI Institute

SetiQuestInfo is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SetiQuestInfo is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SetiQuestInfo.  If not, see<http://www.gnu.org/licenses/>.

Implementers of this code are requested to include the caption
"Licensed through SETI" with a link to setiQuest.org.

For alternate licensing arrangements, please contact
The SETI Institute at www.seti.org or setiquest.org. 

 *******************************************************************************/


/**
 * @file Renderer.java
 *
 * The Renderer for the SETILive project.
 *
 * Project: SetiQuestInfo
 * <BR>
 * Version: 1.0
 * <BR>
 * @author Jon Richards (current maintainer)
 */
package setiquest.renderer;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

//Imports for Jackson/JSON
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import de.undercouch.bson4jackson.BsonFactory;

/**
 * The main class that performs the Renderer actions.
 *
 * @author Jon Richards - The SETI Institute - April 9, 2012
 *
 */
public class Renderer
{
    String archiveDir;
    HashMap sentList = new HashMap();
    Random random = new Random();
    static HashMap fileList = new HashMap();
    Vector groupList = new Vector(100);

    /**
     * Constructor.
     * @param archiveDir the directory containing the compamp data file archives
     */
    public Renderer(String archiveDir)
    {
        this.archiveDir = archiveDir;
        File f = new File(getCompampStorageDirectory());
        f.mkdir();
        Log.log("Renderer started...");
    }

    /**
     * Private class to manage a "group" of compamp files.
     * A group is like one activity, one subchannel, all ZXs.
     * Note: Files are named thus:
     *   2012-01-13_15-16-51_UTC.act8382.target.zx1900.L.compamp
     */
    private class Group
    {
        Vector files = new Vector(3);
        boolean processed = false;
        String date = "";
        String sendResult = "NOT SENT.";

        /**
         * Constructor.
         */
        public Group()
        {
        }

        /**
         * Add a file to the group.
         * @param fi a FileInfo instance representing this file.
         */
        public void add(FileInfo fi)
        {
            files.add(fi);
        }

        /**
         * Determine if a file should be in this group.
         * @param fi a FileInfo instance representing this file.
         */
        public boolean shouldBeInGroup(FileInfo fi)
        {
            if(files.size() == 0) return true;

            FileInfo first = (FileInfo)files.get(0);
            return first.match(fi);
        }

        /** 
         * Get a string representation of this instance.
         * @return a string.
         */
        public String toString()
        {
            String result = "GROUP Count = " + files.size() + ", Processed = " + isProcessed() + "\n";
            result += sendResult + "\n\n";
            for(int i = 0; i<files.size(); i++)
            {
                FileInfo fi = (FileInfo)files.get(i);
                result += fi.toString() + "\n";
            }
            return result;
        }

        /**
         * Determine if this group of files has already been processed.
         * @return tru if has been processed.
         */
        public boolean isProcessed()
        {
            return processed;
        }

        /**
         * Process this file group into a BSON file and send it to the server.
         */
        public void process()
        {
            if(files.size() <= 0) return;

            byte[] pixels1 = null;
            byte[] pixels2 = null;
            byte[] pixels3 = null;
            Data firstData = null;
            int activity = 0;
            int pol1 = 99;
            int pol2 = 99;
            int pol3 = 99;
            int targetBeam1 = 0;
            int targetBeam2 = 0;
            int targetBeam3 = 0;
            int obsType = 99;
            String date = "";
            String time = "";
            String subchannel = "";
            int sigId = 0;

            Log.log("Packaging " + files.size() + " files.");
            for(int i = 0; i<files.size(); i++)
            {
                FileInfo fi = (FileInfo)files.get(i);
                if(i == 0) 
                {
                    firstData = fi.getFirstData();
                    date = fi.date;
                    time = fi.time;
                    subchannel = fi.subchannel;
                    activity = Integer.parseInt(fi.activityId);
                    obsType = getObsTypeInt(fi.activityType);
                }

                if(fi.getBeam() == 1)
                {
                    targetBeam1 = fi.getTargetId();
                    pol1 = Data.polStringToInt(fi.pol); 
                    pixels1 = fi.getData();
                    if(sigId == 0) sigId = Integer.parseInt(fi.sigId);
                }
                else if(fi.getBeam() == 2)
                {
                    targetBeam2 = fi.getTargetId();
                    pol2 = Data.polStringToInt(fi.pol); 
                    pixels2 = fi.getData();
                    if(sigId == 0) sigId = Integer.parseInt(fi.sigId);
                }
                else if(fi.getBeam() == 3)
                {
                    targetBeam3 = fi.getTargetId();
                    pol3 = Data.polStringToInt(fi.pol); 
                    pixels3 = fi.getData();
                    if(sigId == 0) sigId = Integer.parseInt(fi.sigId);
                }
            }

            SetiquestRenderedData sqd = new SetiquestRenderedData();
            SetiquestRenderedData.Beam b1 = sqd.new Beam(1, targetBeam1, pol1, pixels1);
            SetiquestRenderedData.Beam b2 = sqd.new Beam(2, targetBeam2, pol2, pixels2);
            SetiquestRenderedData.Beam b3 = sqd.new Beam(3, targetBeam3, pol3, pixels3);
            SetiquestRenderedData.Beam[] beams = new SetiquestRenderedData.Beam[3];
            beams[0] = b1;
            beams[1] = b2;
            beams[2] = b3;
            System.out.println( beams.toString() );


            //JR - April 09, 2012 - This sometimes is a problem and need to 
            //debug this.
            if(firstData == null)
                System.out.println("\n\nFIRSTDATA == NULL!!!\n\n");

            SetiquestRenderedData sdr = new SetiquestRenderedData(
                    firstData.activityId, 
                    firstData.polarization, 8,
                    768, 129,
                    (float)firstData.rfCenterFrequency,
                    (float)533.333,
                    date, time, 
                    targetBeam1, targetBeam2, targetBeam3,
                    beams,
                    sigId);

            ObjectMapper mapper = new ObjectMapper(new BsonFactory());
            System.out.print( "Act" + sdr.getActivityId() +" " ); 
            
            for ( int j = 0 ; j < 3 ; j++ ) {
                if ( beams[j].getData() != null ) {
                    System.out.print(beams[j].getData().length + " ");
                } else {
                    System.out.print( "null  " );
                }
            }
            
            System.out.print( "SubCh " + String.valueOf(subchannel) + " ");
            System.out.print( "Pol " + sdr.getPol() + " ");
            System.out.print( "Beam Pols " + sdr.getBeam()[0].getPolarization() +
                              " : " + sdr.getBeam()[1].getPolarization() +
                              " : " + sdr.getBeam()[2].getPolarization() );
            
            System.out.println();
                            
            try
            {

                mapper.writer();
                //jmapper.writer();
                Log.log("Writing " + getDir() + "/bson/act" + firstData.activityId + "." + firstData.polarization + "." + obsType + "." + subchannel + ".bson");

                String bsonFilename = getDir() + "/bson/act" + firstData.activityId + "." + firstData.polarization + "." + obsType + "." + subchannel + ".bson";
                mapper.writeValue(new File(bsonFilename), sdr);
                //jmapper.writeValue(new File(jsonFilename), sdr);
                String result = Utils.sendBSONFile(bsonFilename, activity, obsType, firstData.polarization, subchannel);
                System.out.println("bson sent: " + result );
                if(result.contains("201 Created")) sendResult = "SENT.";

            } catch (JsonGenerationException e) {
                e.printStackTrace();
                return;
            } catch (JsonMappingException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }


            processed = true;

            //Backup the files
            for(int i = 0; i<files.size(); i++)
            {
                FileInfo fi = (FileInfo)files.get(i);
                fi.backup(getDir());
            }
        }

        /**
        * Determine if another group's files are polarization counterparts of
        * this one's and if so, combine the other group's pixel data for
        * corresponding zx's with this one.
        * @return boolean true if they correspond and were combined.
        */
        private boolean combine( Group gr ) {
            boolean result = false;
            FileInfo fi, figr, fi1, fi2;
            if ( files.isEmpty() || gr.files.isEmpty() ) return false;
            fi1 = (FileInfo)files.elementAt(0);
            fi2 = (FileInfo)gr.files.elementAt(0);
            if ( fi1.subchannel.equals( fi2.subchannel ) ) {
                for ( int i = 0 ; i < files.size() ; i++ ) {
                    fi = (FileInfo)files.elementAt(i);
                    for ( int j = 0 ; j < gr.files.size() ; j++ ) {
                        figr = (FileInfo)gr.files.elementAt(j);
                        result = fi.combine( figr ) || result;  // order is essential                  
                    }                
                }
                if ( result ) {                
                    for(int i = 0; i < gr.files.size(); i++)
                    {
                        figr = (FileInfo)gr.files.get(i);
                        figr.backup(getDir());
                    }
                }
            }
            return result;
        }

        /**
         * Get the observation type number based on the type name in the file name.
         * LN - Changed off strings to match what FileReceiver is providing.
         * 0 - target (first observation at this frequency for this run)
         * 1 - target1-on
         * 2 - target1off
         * 3 - target2-on
         * 4 - target2off
         * 5 - target3-on
         * 6 - target3off
         * 7 - target4-on
         * 8 - target4off
         * 9 - target5-on
         * 10 - target5off
         * 99 - ???
         */
        private int getObsTypeInt(String obsType)
        {
            if(obsType == null) return 99;
            
            /*
             * targetx-off changed to targetxoff to match actual filenames as
             * FileReceiver delivers them.
             */
            
            else if(obsType.toLowerCase().contains("target1-on"))   return 1;
            else if(obsType.toLowerCase().contains("target1off"))  return 2;
            else if(obsType.toLowerCase().contains("target2-on"))   return 3;
            else if(obsType.toLowerCase().contains("target2off"))  return 4;
            else if(obsType.toLowerCase().contains("target3-on"))   return 5;
            else if(obsType.toLowerCase().contains("target3off"))  return 6;
            else if(obsType.toLowerCase().contains("target4-on"))   return 7;
            else if(obsType.toLowerCase().contains("target4off"))  return 8;
            else if(obsType.toLowerCase().contains("target5-on"))   return 9;
            else if(obsType.toLowerCase().contains("target5off"))  return 10;
            else if(obsType.toLowerCase().contains("target"))       return 0;
            else return 99;

        }

    }

    /**
     * Private class used to fileter files.
     */
    private class FileFilter implements FilenameFilter
    {
        String extension;
        String[] matchList;

        public FileFilter(String extension, String[] matchList)
        {
            this.extension = extension;
            this.matchList = new String[matchList.length];

            for(int i = 0; i<matchList.length; i++)
                this.matchList[i] = matchList[i];
        }

        public boolean accept(File dir, String name)
        {
            if ( new File( dir, name ).isDirectory() )
            {
                return false;
            }
            boolean isMatch = false;
            for(int i = 0; i<matchList.length; i++)
                if(name.contains(matchList[i])) isMatch = true;
            return isMatch && name.contains(extension);
        }
    }

    /**
     * Get the archive directory path. This is where the fileReceive puts
     * all the compamp files.
     * @return the full path as a String.
     */
    private String getDir()
    {
        return archiveDir;
    }

    /**
     * Get the directory the compams will be copied to when rendering is complete.
     * @return a string of the full path directory name.
     */
    private String getCompampStorageDirectory()
    {
        Calendar cal = Calendar.getInstance();

        return getDir() + "/" + 
            cal.get(Calendar.YEAR) + "-" +
            String.format("%02d", (cal.get(Calendar.MONTH)+1)) + "-" +
            String.format("%02d", cal.get(Calendar.DAY_OF_MONTH));
    }


    /**
     * Comb the archive directory for compamp files.
     * @param rORl ".R." or ".L." pol.
     * @return true if any file groups sent to processing.
     */
    public boolean combForFiles(String rORl)
    {
        fileList.clear();
        groupList.clear();

        //if(bfSigStats[bfNum-1].getDx() < 1) 
        //	return bfSigStats[bfNum-1].getCompampFilename();
        //Log.log("Combing for files in: " + getDir());

        long lastModified = 0;
        String latestFilename = null;

        File dir = new File(getDir());
        String[] children = null;
        String[] matchList = new String[1];
        matchList[0] = rORl;
        
        /*
         * LN One-shot delay to make sure file transfer is stable prior to
         * reading them.
         */
        
        //LN Need a couple new variables for one-shot
        int lastNumChildren = 0; //LN Initialize the memory
        int delta; //LN Variable for change in number of number of files.
        int oneShotDelay = 5000; //LN Should be global variable or arg(?). 
                                  // Takes away from follow-up timing. 
                                  // Keep low.
        
        // LN Loop directory read to wait for  sure file transfer is stable
        do {
            children = dir.list(new FileFilter(".compamp", 
                        matchList));
            delta = children.length - lastNumChildren;
            lastNumChildren = children.length;
            //System.out.println ("waiting..." + String.valueOf(delta));
            delay(oneShotDelay);
        } while( delta != 0 );
        
        if (children != null)
        {
            for (int i=0; i<children.length; i++)
            {
                synchronized(fileList)
                {
                    if(fileList.get(children[i]) == null)
                    {
                        File f = new File(getDir() + "/" + children[i]);
                        // if file is >= 5 minutes old - delete it.
                        if((System.currentTimeMillis() - f.lastModified()) >= (10*60*1000))
                        {
                            Log.log("Deleting old file: " + children[i] + " Times: " + 
                                    System.currentTimeMillis() + ", " + 
                                    f.lastModified());
                            f.delete();
                        }
                        else if(f.length() == 71208)
                        {
                            FileInfo fi = new FileInfo(getDir() + "/" + children[i]);
                            fileList.put(children[i], fi);
                        }
                    }
                }

            }
        }


        //Make groups of the files.
        if(!processFiles()) return false;
        
        combinePolarizations();

        //Process any file groups.
        return processGroups();

    }

    /**
     * Go through the detected files and create groups.
     * @return tru if any groups created.
     */
    private boolean processFiles()
    {
        //JR - April 09, 2012 - For testing, the first file
        //in the group gets faked as a followup.
        boolean firstFile = true;

        //Process the file list into groups.
        groupList.clear();

        synchronized(fileList)
        {
            Set set = fileList.entrySet();
            Iterator i = set.iterator();
            while(i.hasNext())
            {
                Map.Entry me = (Map.Entry)i.next();
                FileInfo fi = (FileInfo)me.getValue();
                insertFileIntoGroup(fi);
                if(firstFile == true)
                {
                    firstFile = false;
                    //postFakeFollowup(fi);
                }
            }
        }

        return true;
    }

    /**
     * For testing only. Take a file and create a follwup that Charlie testing
     * can use.
     * @param fi the file information for one file.
     */
    private void postFakeFollowup(FileInfo fi)
    {
        String fid = fi.sigId;
        String actid = fi.activityId;
        String tid = fi.targetId;
        String ppol = fi.pol;
        String subch = fi.subchannel;
        String ffreq = "" + fi.getFirstData().rfCenterFrequency;
        String beamNo = "" + fi.getBeam();

        if(ppol.equals("R")) ppol ="right";
        else ppol = "left";
        //Since we are faking it here - if a signal has sigId == 0, create a followupid == random
        //number between 1 and 100000.
        Random rand = new Random(System.currentTimeMillis()); //seed with current time.
        fid = "" + (rand.nextInt(100000) + 1);

        //read in the template
        String jsontemplate = "";
        try
        {
            FileReader input = new FileReader("/home/setiquest/public/followups-orig.json");
            BufferedReader bufRead = new BufferedReader(input);
            jsontemplate = bufRead.readLine();
            bufRead.close();
            input.close();
        }
        catch (Exception ex)
        {
            Log.log("ERROR: postFakeFollowup() can't read /home/setiquest/public/followups-orig.json");
            return;
        }

        //Log.log("JSON Template: " + jsontemplate);

        //Replace values in the template.
        String json = jsontemplate.replace("fid", fid);
        json = json.replace("actid", actid);
        json = json.replace("tid", tid);
        json = json.replace("ppol", ppol);
        json = json.replace(":subch", ":" + subch);
        json = json.replace("ffreq", ffreq);
        json = json.replace("bno", beamNo);

        Log.log(json);

        try
        {
            FileWriter fw = new FileWriter("/home/setiquest/public/followups.json",false);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(json);
            bw.close();
            fw.close();
        }
        catch (Exception ex)
        {
            Log.log("ERROR: postFakeFollowup() can't write: " + json + ", to /home/setiquest/public/followups.json");
        }


    }

    /**
     * Insert a file into a group. A new group is created if there is
     * not already a group for it.
     * @param fi the FileInfo instance representing this file.
     */
    private void insertFileIntoGroup(FileInfo f)
    {
        boolean inserted = false;

        for(int i = 0; i<groupList.size(); i++)
        {
            Group gr = (Group)groupList.get(i);
            if(gr.shouldBeInGroup(f))
            {
                inserted = true;
                gr.add(f);
            }
        }

        if(inserted == false)
        {
            Group gr = new Group();
            gr.add(f);
            groupList.add(gr);
        }
    }

    /**
     * Process all the groups.
     * @return true if any processes.
     */
    private boolean processGroups()
    {
        for(int i = 0; i<groupList.size(); i++)
        {
            Group gr = (Group)groupList.get(i);
            gr.process();
        }
        return true;
    }


    /**
     * Finds polarization counterpart groups in groupList, replaces pixels in
     * one with combined values, then removes the other one from groupList.
     */
    
    private void combinePolarizations() {
        
        Group refGroup;
        Group tmpGroup;
        int tmpIndex;
        Vector tmpGroupList = (Vector)groupList.clone();
        int refIndex = 0;
        while ( refIndex < groupList.size() - 1 ) {
            refGroup = (Group)groupList.elementAt( refIndex );
            boolean isCombined = false;
            tmpIndex = refIndex + 1;
            while ( tmpIndex < tmpGroupList.size() && !isCombined ) {
                tmpGroup = (Group)tmpGroupList.elementAt( tmpIndex );
                // Combine groups if from same zx
                if ( isCombined = refGroup.combine( tmpGroup ) )  {
                    groupList.removeElementAt( tmpIndex );
                    tmpGroupList.removeElementAt( tmpIndex );
                }
                tmpIndex++;
            }
            refIndex++;
        }
    }
    
    /**
     * Sleep for a number of milliseconds.
     * @param ms the number of milliseconds to sleep.
     */
    public static void delay(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(Exception ex)
        {
            Log.log("Renderer: Sleep exception: " + ex.getMessage());
        }
    }

    /**
     * Create a string representation of the files.
     * @return the string representation of the files.
     */
    public String toString()
    {
        String result = "Dir: " + getDir() + "\n";

        synchronized(fileList)
        {
            Set set = fileList.entrySet();
            Iterator i = set.iterator();
            while(i.hasNext())
            {
                Map.Entry me = (Map.Entry)i.next();
                FileInfo fi = (FileInfo)me.getValue();
                result += fi.toString() + "\n";
            }
        }


        return result;
    }

    /**
     * Create a string representation of the groups.
     * @return a string representation of the groups.
     */
    public String groupsToString()
    {
        if(groupList.size() == 0) return null;

        String result = "GROUP LIST:\n";
        result += "Num of groups: " + groupList.size() + "\n";
        result += "Num of files:  " + fileList.size() + "\n";

        for(int i = 0; i<groupList.size(); i++)
        {
            result += "************\nGROUP NUMBER = " + (i+1) + "\n";
            Group gr = (Group)groupList.get(i);
            result += gr.toString();
            result += "\n************\n";
        }
        return result;
    }

    /**
     * Main entry point for the program.
     *
     * @param cmdLine the command line arguments
     */
    public static void main(String[] cmdLine)
    {

        //The only command line parameter is to specify a new
        //properties file other than the default.
        if(cmdLine.length != 0)
        {
            Utils.setParamFileName(cmdLine[0]);
        }

        Log.log("*****************************");
        Log.log(Utils.propertiesToString());
        Log.log("*****************************");

        //Loop forever, even if there is an exception.
        while(true)
        {
            try
            {
                Renderer fm = new Renderer(Utils.getDataDir());
                // Uncomment if not running as a thread below
                //ScheduleMonitor sm = new ScheduleMonitor();
                long scheduleUpdateMillis = 1000 * 
                        Long.valueOf( Utils.getScheduleUpdatePeriod() );
                long timeNow;
                long lastScheduleUpdate = System.currentTimeMillis();

                long lastTimeSecs = System.currentTimeMillis()/1000;
                long lastPurgeSecs = System.currentTimeMillis()/1000;
                long lastCompampPurgeSecs = System.currentTimeMillis()/1000;
                
                //LN Create target directories if they don't exist
                File dataDir = new File(Utils.getDataDir());
                File bsonDir = new File(Utils.getBsonDataDir());
                if (!dataDir.exists()) dataDir.mkdir();
                if (!bsonDir.exists()) bsonDir.mkdir();

                //Loop forever 
                while(true)
                {
                    timeNow = System.currentTimeMillis();
                    
                    // Check if schedule check is due
                    if ( ( timeNow - lastScheduleUpdate - scheduleUpdateMillis ) >= 0 ) {
                        lastScheduleUpdate = timeNow;
                        ScheduleMonitor sm = new ScheduleMonitor();
                        new Thread(sm).start();
                        //sm.run(); // Replace two above lines for non-thread.
                    }
                        
                    fm.combForFiles("");

                    String info = fm.groupsToString();
                    if(info != null)
                      Log.log("INFO: " + info);

                    //Every hour purge old bson files.
                    long thisPurgeSecs = System.currentTimeMillis()/1000;
                    if((thisPurgeSecs - lastPurgeSecs) >= (60*60))
                    {
                        Log.log("Purging BSON files from " + Utils.getBsonDataDir());
                        Utils.purgeBsonDir(Utils.getBsonDataDir(), 7);
                        lastPurgeSecs = thisPurgeSecs;
                    }

                    //Sleep for 2 seconds.
                    delay(2000);

                }

            }
            catch (Exception ex)
            {
                Log.log("ERROR: The Renderer main loop got an exception: " + ex.getMessage());
                Log.log("Restarting.");
            }
        }


    }

}
