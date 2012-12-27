/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package setiquest.renderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;


/**
 *
 * @author nigra
 */
public class ScheduleMonitor implements Runnable {
    
    String scheduleUrl;
    long activeDelay;
    String scheduleXmlStr, ignoredCommands;
    HttpGet httpGet;
    HttpPost httpPost;
    int maxMinutesForContiguous = 10;
    
    ScheduleMonitor() {
        
        this.scheduleUrl = Utils.getScheduleURL();
        this.ignoredCommands = Utils.getIgnoredCommands();
        this.activeDelay = Long.valueOf(Utils.getActiveDelayTime()) * 60000;
        this.httpGet = new HttpGet( this.scheduleUrl );
        
    }
    
    @Override
    public void run() {

        int ind1, ind2, tempInt;
        boolean gotData = true, current, contiguous, ignored;
        String[] timeInfo;
        long timeNow, timeStart, timeEnd, timeNext, timeStartNext;
        String commandStr;
        //httpClient = null;
        //InputStream instream = null;
        //HttpResponse response;
        //HttpEntity entity = null;
        
        try {
            HttpClient httpClient = new DefaultHttpClient();

            HttpResponse response = httpClient.execute(httpGet);

            System.out.println("Getting schedule: " + response.getStatusLine());

            HttpEntity entity = response.getEntity();

            if (entity != null) {
                InputStream instream = entity.getContent();
                try {

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(instream));
                    
                    do {
                        timeInfo = getScheduleInfo( reader );
                    } while ( ignoredCommands.indexOf( "," + timeInfo[1] + "," ) > -1 );

                    current = timeInfo[0].equals( "true" );
                    commandStr = timeInfo[1];
                    timeStart = Long.valueOf(timeInfo[2]);
                    timeEnd = Long.valueOf(timeInfo[3]);
                    
                    // Check if we have contiguous
                    do {
                        do {
                            timeInfo = getScheduleInfo( reader );
                        } while ( ignoredCommands.indexOf( "," + timeInfo[1] + "," ) > -1 );

                        if ( Long.valueOf( timeInfo[2] ) 
                                <= timeEnd + 60000 * maxMinutesForContiguous ) {
                            timeEnd = Long.valueOf( timeInfo[3] );
                            commandStr = commandStr + ", " + timeInfo[1];
                            contiguous = true;
                        } else {
                            contiguous = false;
                        }
                    } while( contiguous );
                    
                    instream.close();
                    
                    httpPost = new HttpPost(Utils.getNextStatusURL());

                    MultipartEntity reqEntity = new MultipartEntity();
                    reqEntity.addPart("current", new StringBody( Boolean.toString( current ) ) );
                    reqEntity.addPart("starttime", new StringBody( Long.toString( timeStart ) ) );
                    reqEntity.addPart("endtime", new StringBody( Long.toString( timeEnd ) ) );
                    reqEntity.addPart("password", new StringBody( Utils.getPassword() ) );
                    httpPost.setEntity(reqEntity);
                    System.out.println( "Commands: " + commandStr );
                    System.out.println( "Posting schedule info: " + " " +
                                         Boolean.toString( current ) + " " +
                                         Long.toString( timeStart ) + " " +
                                         Long.toString( timeEnd ) );
                    response = httpClient.execute(httpPost);
                    System.out.println("Posted schedule info: " + response.getStatusLine());
                    
                } catch (IOException ex) {

                    System.out.println( ex.toString() );
                    throw ex;

                } catch (RuntimeException ex) {

                    System.out.println( ex.toString() );
                    httpGet.abort();
                    throw ex;

                } finally {
                    instream.close();
                }
                httpClient.getConnectionManager().shutdown();
                
            } else {                
                gotData = false;
            }

        } catch (IOException ex ) {
            System.out.println( ex.toString() );
        }
    }
    
    private String[] getScheduleInfo( BufferedReader reader ) {
        
        String[] result = new String[4];
        String command = "";
        String responseStr;
        boolean isCurrent = false, gotData = false;
        long tStart = 0, tEnd = 0;
        
        try {
            do {
                responseStr = reader.readLine();
            } while ( responseStr != null && responseStr.indexOf( "<obs current=" ) < 0 );

            if ( responseStr != null ) {                        
                isCurrent = responseStr.indexOf( "yes" ) >= 0;
                responseStr = reader.readLine();
                
                if ( responseStr != null ) {
                    command = getCommandFromLine( responseStr );
                } else {
                    command = "";
                }
                do {
                    responseStr = reader.readLine();
                } while ( responseStr != null && responseStr.indexOf( "<start>" ) < 0 );

                tStart = this.getTimeFromSchedule( reader );

                do {
                    responseStr = reader.readLine();
                } while ( responseStr != null && responseStr.indexOf( "<end>" ) < 0 );

                tEnd = this.getTimeFromSchedule( reader );

            } else gotData = false;
        } catch(IOException ex) {
            System.out.println( ex.toString() );
        }
        result[0] = isCurrent ? "true" : "false";
        result[1] = command;
        result[2] = Long.toString( tStart );
        result[3] = Long.toString( tEnd );
        
        return result;
    }
    
    /**
    * Get a time from the schedule
    */

    private long getTimeFromSchedule( BufferedReader reader ) throws IOException {

        long time;
        int ind1, ind2;
        boolean gotData = true;
        String yrStr = "", monStr = "", dayStr = "", hrStr ="", minStr = "";
        String responseStr;
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Calendar calendar = GregorianCalendar.getInstance( tz );

        if ( ( responseStr = reader.readLine() ) != null ) {
            ind1 = responseStr.indexOf("</year>");
            yrStr = responseStr.substring( ind1 - 4, ind1 );
        } else gotData = false;

        if ( ( responseStr = reader.readLine() ) != null ) {
            ind1 = responseStr.indexOf("</month>");
            monStr = responseStr.substring( ind1 - 2, ind1 );
        } else gotData = false;

        if ( ( responseStr = reader.readLine() ) != null ) {
            ind1 = responseStr.indexOf("</day>");
            dayStr = responseStr.substring( ind1 - 2, ind1 );
        } else gotData = false;

        if ( ( responseStr = reader.readLine() ) != null ) {
            ind1 = responseStr.indexOf("</hour>");
            hrStr = responseStr.substring( ind1 - 2, ind1 );
        } else gotData = false;

        if ( ( responseStr = reader.readLine() ) != null ) {
            ind1 = responseStr.indexOf("</minute>");
            minStr = responseStr.substring( ind1 - 2, ind1 );
        } else gotData = false;

        if ( gotData ) {
        calendar.set( Integer.valueOf( yrStr ),
                            Integer.valueOf( monStr ) - 1,
                            Integer.valueOf( dayStr ),
                            Integer.valueOf( hrStr ),
                            Integer.valueOf( minStr ),
                            0 );

            time = calendar.getTimeInMillis();

        } else time = 0;

        return time;
    }
    
    private String getCommandFromLine( String line ) {
        
        int ind1, ind2;
        if ( ( ind1 = line.indexOf("<command>") ) > -1 & 
             ( ind2 = line.indexOf("</command>") ) > -1 ) {
            return line.substring( ind1 + 9, ind2 );
        } else {
            return null;
        }
    
    }

}

