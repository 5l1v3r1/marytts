/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */

package marytts.tools.dbselection;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import marytts.features.FeatureDefinition;



/**
 * Main class to be run over a database for selection
 * 
 * @author Anna Hunecke
 *
 */
public class DatabaseSelector{
    
    //  locale
    private static String locale;

    //the feature definition for the feature vectors
    public static FeatureDefinition featDef;
    //the file containing the feature definition
    private static String featDefFileName;

    //the file containing the filenames from which to select
//    private static String basenameFileName;

    //the file containing the coverage data needed 
    //to initialise the algorithm
    private static String initFileName;
    //the directory to print the selection results to
    private static String selectionDirName;
    //the config file for the coverage definition
    private static String covDefConfigFileName;
    //the stop criterion (as string)
    private static String stopCriterion;
    //the list of sentences from which to select
 
//    private static String[] basenameList;
    private static int[] idSentenceList;
    
    //the log file to log the result to
    private static String overallLogFile;
    //if true, feature vectors are kept in memory
    private static boolean holdVectorsInMemory;
    //if true, print more information to command line
    private static boolean verbose;
    //if true, print a table containing the coverage 
    //development over time
    private static boolean logCovDevelopment;
    //file containing the list of sentences that
    //are already selected
    
    
    private static String selectedSentsFile;
//    private static boolean loadSelectedSentences=false;
    //list of selected sentences
    //private static List selectedSents;
    private static List<Integer> selectedIdSents;
    
    //file containing the list of sentences that
    //are not wanted on the basename list
    private static String unwantedSentsFile;
    
    private static String selectedSentencesTableName;
    private static String tableDescription;
    
    protected static DBHandler wikiToDB;
    //  mySql database 
    private static String mysqlHost;
    private static String mysqlDB;
    private static String mysqlUser;
    private static String mysqlPasswd;
    
    /**
     * Main method to be run from the directory where the data is.
     * Expects already computed unit features in directory unitfeatures
     * 
     * @param args the command line args (see printUsage for details)
     */
    public static void main(String[] args)throws Exception{
        main2(args,null);
       // main1(args);
    }

    
    public static void main1(String[] args)throws Exception{
      
      byte[][] vecArray = main2(args,null);
      
      main2(args,vecArray);
        
    }
    
    
    /**
     * Main method to be run from the directory where the data is.
     * Expects already computed unit features in directory unitfeatures.
     * Can be given an array of feature vectors - this is useful if the 
     * program is run several times with the same feature vectors.
     * 
     * @param args the command line args (see printUsage for details)
     * @param vectorArray the array of feature vectors
     * 
     * @return the array of feature vectors used in the current pass
     */
    public static byte[][] main2(String[] args,byte[][] vectorArray)throws Exception{
        /* Sort out the filenames and dirs for the logfiles */
        System.out.println("Starting Database Selection...");
        
        long time = System.currentTimeMillis();
        PrintWriter logOut;
        
        String dateString = "", dateDir = "";
      /*  DateFormat fullDate = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss");
        DateFormat day = new SimpleDateFormat("dd_MM_yyyy");
        Date date = new Date();
        dateString = fullDate.format(date);
        dateDir = day.format(date);*/
                
        System.out.println("Reading arguments ...");
        StringBuffer logBuf = new StringBuffer();
        if (!readArgs(args,logBuf))
            return null;

        //make sure the stop criterion is allright
        SelectionFunction selFunc = new SelectionFunction();
        if (!selFunc.stopIsOkay(stopCriterion)){
            System.out.println("Stop criterion format is wrong");
            printUsage();
            return null;
        }

        //make various dirs
        File selectionDir = new File(selectionDirName);
        if (!selectionDir.exists())
            selectionDir.mkdir();
        File dateDirFile = new File(selectionDirName+dateDir);
        if (!dateDirFile.exists())
            dateDirFile.mkdir();

        //open log file
        String filename = selectionDirName + dateDir + "/selectionLog_" + dateString + ".txt";
        try{
            logOut = new PrintWriter(new BufferedWriter(new FileWriter(new File(filename))),true);
        } catch (Exception e){
            e.printStackTrace();
            throw new Error("Error opening logfile");
        }
        //print date and arguments to log file
        logOut.println("Date: "+dateString);
        logOut.println(logBuf.toString());
        
        wikiToDB = new DBHandler(locale);
        
        // Check if name of selectedSentencesTable has to be changed
        if(selectedSentencesTableName != null)
          wikiToDB.setSelectedSentencesTableName(selectedSentencesTableName);
        else
          System.out.println("Current selected sentences table name = " + selectedSentencesTableName);
              
        wikiToDB.createDBConnection(mysqlHost,mysqlDB,mysqlUser,mysqlPasswd);
        
        
        /* Load the filenames */   
 //       System.out.println("Loading basenames...");
 //       loadBasenames();

        /* Read in the feature definition */
        System.out.println("\nLoading feature definition...");
        BufferedReader uttFeats = new BufferedReader(new InputStreamReader(new FileInputStream(new File( featDefFileName )), "UTF-8"));
        featDef = new FeatureDefinition(uttFeats, false);  
        uttFeats.close();

        System.out.println("TARGETFEATURES:" + featDef.getNumberOfFeatures() + " =  " + featDef.getFeatureNames());
        
        /* Initialise the coverage definition */
        System.out.println("\nInitiating coverage...");
        CoverageDefinition covDef = new CoverageDefinition(featDef,covDefConfigFileName,holdVectorsInMemory,vectorArray);
        
        // If the selectedSentencesTable is new, (does not exist) then a new table
        // will be created so the selected and unwanted fields in the dbselection table 
        // should be initialised, set to selected=false and unwanted=false for all the realible
        // sentences.
        // create the selectedSentences table 
        // CHECK if there is an old table it should be deleted? 
        wikiToDB.createSelectedSentencesTable(stopCriterion, featDefFileName, covDefConfigFileName);
        wikiToDB.setTableDescription(wikiToDB.getSelectedSentencesTableName(), tableDescription, 
                                     stopCriterion,featDefFileName, covDefConfigFileName);

        long startTime = System.currentTimeMillis();
        File covSetFile = new File(initFileName);
        boolean readCovFromFile = true;
        boolean vectorArrayNull = (vectorArray == null);
        if (!covSetFile.exists()){
            //coverage has to be initialised
            readCovFromFile = false;
 //           basenameList = covDef.initialiseCoverage();   // pass here the dbselection DB
            idSentenceList = covDef.initialiseCoverage(wikiToDB, verbose); 
            System.out.println("\nWriting coverage to file "+initFileName);
            covDef.writeCoverageBin(initFileName);   // generate here the file taking the basenames from dbselection DB
        } else {                
            //coverage can be read from file 
            //covDef.readCoverageBin(initFileName,featDef,basenameList);

            idSentenceList = wikiToDB.getIdListOfType("dbselection", "reliable=true");
            covDef.readCoverageBin(wikiToDB, initFileName,featDef,idSentenceList);
        }
        
        if (vectorArrayNull) vectorArray = covDef.getVectorArray();
        
        /* add already selected sentences to cover */
        System.out.println("\nAdd to cover already selected sentences marked as unwanted=false.");
        selectedIdSents = new ArrayList<Integer>();
        addSelectedSents(covDef);
        
        
        //if (selectedSentsFile != null){     // at start this file should not exist??
        //    addSelectedSents(covDef);
        //} //else {
            //selectedSents = new ArrayList();
        //}
        
        /* remove unwanted sentences from basename list */
        //if (unwantedSentsFile != null){
        System.out.println("\nRemoving selected sentences marked as unwanted=true.");
        removeUnwantedSentences();    
           // maybe here we can mark the sentence as unwanted=true or selected=false  
        //}
        
        
        long startDuration = System.currentTimeMillis() -startTime;
        if (verbose)
            System.out.println("Startup took "+startDuration+" milliseconds");
        logOut.println("Startup took "+startDuration+" milliseconds");
        
        /* print text corpus statistics */
        if (!readCovFromFile){
            //only print if we did not read from file
            filename = selectionDirName+"textcorpus_distribution.txt";
            System.out.println("Printing text corpus statistics to "+filename+"...");       
            try{
                covDef.printTextCorpusStatistics(filename);
            } catch (Exception e){
                e.printStackTrace();
                throw new Error("Error printing statistics");
            }
        }

        //print settings of the coverage definition to log file 
        covDef.printSettings(logOut);

        /* Start the algorithm */
        System.out.println("\nSelecting sentences...");
       
        //selFunc.select(selectedSents,covDef,logOut,basenameList,holdVectorsInMemory,verbose);
        selFunc.select(selectedIdSents,covDef,logOut,idSentenceList,holdVectorsInMemory,verbose,wikiToDB);

        /* Store list of selected files */
        filename = selectionDirName+dateDir + "/selectionResult_" + dateString + ".txt";
        //storeResult(filename,selectedSents);
        storeResult(filename,selectedIdSents);

        /* print statistics */
        System.out.println("Printing selection distribution and table...");
        String disFile = selectionDirName+dateDir + "/selectionDistribution_" + dateString + ".txt";
        String devFile = selectionDirName+dateDir + "/selectionDevelopment_" + dateString + ".txt";
        try{
            covDef.printSelectionDistribution(disFile,devFile,logCovDevelopment);
        } catch (Exception e){
            e.printStackTrace();
            throw new Error("Error printing statistics");
        }

        if (overallLogFile != null){
            //append results to end of overall log file
            PrintWriter overallLogOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
                                        new File(overallLogFile),true),"UTF-8"),true);
            overallLogOut.println("*******************************\n" + "Results for "+dateString+":");
            
            //overallLogOut.println("number of basenames "+basenameList.length);
            overallLogOut.println("number of basenames "+idSentenceList.length);
            
            overallLogOut.println("Stop criterion "+stopCriterion);
            covDef.printResultToLog(overallLogOut);
            overallLogOut.close();
        }

        //print timing information
        long elapsedTime = System.currentTimeMillis() - time;
        double minutes = (double)elapsedTime/(double)1000/(double)60;
        System.out.println("Selection took "+minutes+" minutes("+elapsedTime+" milliseconds)");
        logOut.println("Selection took "+minutes+" minutes ("+elapsedTime+" milliseconds)");
        logOut.flush();
        logOut.close();
        
 //       checkSelectedSentences();
 //       wikiToDB.getTableDescription(wikiToDB.getSelectedSentencesTableName());
        
        wikiToDB.closeDBConnection();    

        System.out.println("All done!");   
        return vectorArray;
    }

    /**
     * Read and check the command line arguments
     * 
     * @param args the arguments
     * @param log a StringBufffer for logging
     * @return true if args can be parsed and all essential args are there,
     *         false otherwise 
     */
    private static boolean readArgs(String[] args,StringBuffer log){
        //initialise default values
        locale = null;
        selectionDirName = null;
        initFileName = null;
        covDefConfigFileName = null;
        overallLogFile = null;
        holdVectorsInMemory = true;
        verbose = false;
        logCovDevelopment = false;
        selectedSentsFile = null;
        unwantedSentsFile = null;
        mysqlHost = null;
        mysqlDB = null;
        mysqlUser = null;
        mysqlPasswd = null;
        selectedSentencesTableName = null;
        tableDescription = "";
        
        int i=0;
        int numEssentialArgs = 0;
        
        //loop over args
        while (args.length > i){ 
            if (args[i].equals("-locale")){
                if (args.length > i+1){
                    i++;
                    locale = args[i];
                    log.append("locale : "+args[i]+"\n");
                    System.out.println("  locale : "+args[i]);
                    numEssentialArgs++;
                } else {
                    System.out.println("No locale.");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-mysqlHost")){
                if (args.length > i+1){
                    i++;
                    mysqlHost = args[i];
                    log.append("mysqlHost : "+args[i]+"\n");
                    System.out.println("  mysqlHost : "+args[i]);
                    numEssentialArgs++;
                } else {
                    System.out.println("No mysqlHost.");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-mysqlDB")){
                if (args.length > i+1){
                    i++;
                    mysqlDB = args[i];
                    log.append("mysqlDB : "+args[i]+"\n");
                    System.out.println("  mysqlDB : "+args[i]);
                    numEssentialArgs++;
                } else {
                    System.out.println("No mysqlDB.");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-mysqlUser")){
                if (args.length > i+1){
                    i++;
                    mysqlUser = args[i];
                    log.append("mysqlUser : "+args[i]+"\n");
                    System.out.println("  mysqlUser : "+args[i]);
                    numEssentialArgs++;
                } else {
                    System.out.println("No mysqlUser.");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-mysqlPasswd")){
                if (args.length > i+1){
                    i++;
                    mysqlPasswd = args[i];
                    log.append("mysqlPasswd : "+args[i]+"\n");
                    System.out.println("  mysqlPasswd : "+args[i]);
                    numEssentialArgs++;
                } else {
                    System.out.println("No mysqlPasswd.");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-featDef")){
                if (args.length > i+1){
                    i++;
                    featDefFileName = args[i];
                    log.append("FeatDefFileName : "+args[i]+"\n");
                    System.out.println("  FeatDefFileName : "+args[i]);
                    numEssentialArgs++;
                } else {
                    System.out.println("No featDef file");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-initFile")){
                if (args.length > i+1){
                    i++;
                    initFileName = args[i];
                    log.append("initFile : "+args[i]+"\n");
                    System.out.println("  initFile : "+args[i]);
                } else {
                    System.out.println("No initFile");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-selectedSentences")){
                if (args.length > i+1){
                    i++;
                    selectedSentsFile = args[i];
                    log.append("selectedSentences file : "+args[i]+"\n");
                    System.out.println("  selectedSentences file: "+args[i]);
                } else {
                    System.out.println("No selectedSentences file");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-tableName")){
                if (args.length > i+1){
                    i++;
                    selectedSentencesTableName = args[i];
                    log.append("selectedSentencesTable name : "+args[i]+"\n");
                    System.out.println("  selectedSentencesTable name: "+args[i]);
                } else {
                    System.out.println("No selectedSentencesTable name");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-tableDescription")){
                if (args.length > i+1){
                    i++;
                    tableDescription = args[i];
                    log.append("tableDescription : "+args[i]+"\n");
                    System.out.println("  tableDescription: "+args[i]);
                } else {
                    System.out.println("No tableDescription");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-unwantedSentences")){
                if (args.length > i+1){
                    i++;
                    unwantedSentsFile = args[i];
                    log.append("unwantedSentences file : "+args[i]+"\n");
                    System.out.println("  unwantedSentences file: "+args[i]);
                } else {
                    System.out.println("No unwantedSentences file");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            
            if (args[i].equals("-vectorsOnDisk")){
                holdVectorsInMemory = false;
                log.append("vectorsOnDisk");
                System.out.println("  vectorsOnDisk");
                i++;
                continue;
            }
            if (args[i].equals("-verbose")){
                verbose = true;
                log.append("verbose");
                System.out.println("  verbose");
                i++;
                continue;
            }
            if (args[i].equals("-logCoverageDevelopment")){
                logCovDevelopment = true;
                log.append("logCoverageDevelopment");
                System.out.println("  logCoverageDevelopment");
                i++;
                continue;
            }
            if (args[i].equals("-selectionDir")){
                if (args.length > i+1){
                    i++;
                    selectionDirName = args[i];
                    //make sure we have a slash at the end
                    char lastChar = 
                        selectionDirName.charAt(selectionDirName.length()-1);
                    if (Character.isLetterOrDigit(lastChar)){
                        selectionDirName = selectionDirName+"/"; 
                    }
                    log.append("selectionDir : "+args[i]+"\n");
                    System.out.println("  selectionDir : "+args[i]);
                } else {
                    System.out.println("No selectionDir");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-coverageConfig")){
                if (args.length > i+1){
                    i++;
                    covDefConfigFileName = args[i];
                    log.append("coverageConfig : "+args[i]+"\n");
                    System.out.println("  coverageConfig : "+args[i]);
                } else {
                    System.out.println("No coverageConfig");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            if (args[i].equals("-stop")){
                StringBuffer tmp = new StringBuffer();
                i++;
                while (args.length > i){                    
                    if (args[i].startsWith("-")) break;
                    tmp.append(args[i]+" ");  
                    i++;
                } 
                stopCriterion = tmp.toString();
                log.append("stop criterion : "+stopCriterion+"\n");
                System.out.println("  stop criterion : "+stopCriterion);
                numEssentialArgs++;
                continue;
            }
            if (args[i].equals("-overallLog")){
                if (args.length > i+1){
                    i++;
                    overallLogFile = args[i];
                    log.append("overallLogFile : "+args[i]+"\n");
                    System.out.println("  overallLogFile : "+args[i]);
                    numEssentialArgs++;
                } else {
                    System.out.println("No overall log file");
                    printUsage();
                    return false;
                }
                i++;
                continue;
            }
            i++;
        }
        System.out.println();
        if (numEssentialArgs<9){
            //not all essential arguments were given
            System.out.println("You must at least specify locale, mysql (host,user,paswd,DB), selectedSentencesTableName" +
                    "featureDefinition file, stop criterion and coverage config file.");
            printUsage();
            return false;
        } 
        if(selectedSentencesTableName==null){
            System.out.println("Please provide a name for the selectedSentencesTable.");
            printUsage();
            return false;  
        }
        if (selectionDirName == null){
            selectionDirName = "./selection/";
        }
        if (initFileName == null){
            initFileName = selectionDirName+"init.bin";
        }
        if (covDefConfigFileName == null){
            covDefConfigFileName = selectionDirName+"covDef.config";   
        }
        
        return true;
    }



    /**
     * Print usage of main method
     * to standard out
     */
    private static void printUsage(){
        System.out.println("\nUsage: "
                +"java DatabaseSelector -locale language -mysqlHost host -mysqlUser user -mysqlPasswd passwd -mysqlDB wikiDB \n"
                +"        -tableName selectedSentencesTableName -featDef file -stop stopCriterion \n"
                +"        [-coverageConfig file -initFile file -selectedSentences file -unwantedSentences file ]\n" 
                +"        [-tableDescription a brief description of the table ]\n"
                +"        [-vectorsOnDisk -overallLog file -selectionDir dir -logCoverageDevelopment -verbose]\n\n"     
                +"Arguments:\n"
                +"-tableName selectedSentencesTableName : The name of a new selection set, change this name when\n"
                +"    generating several selection sets. FINAL name will be: \"locale_name_selectedSenteces\". \n"
                +"    where name is the name provided for the selected sentences table.\n"
                +"-tableDescription : short description of the selected sentences table. (default: empty)"
                +"-featDef file : The feature definition for the features\n"
                +"-stop stopCriterion : which stop criterion to use. There are five stop criteria. \n"
                +" They can be used individually or can be combined:\n"
                +"  - numSentences n : selection stops after n sentences\n"
                +"  - simpleDiphones : selection stops when simple diphone coverage has reached maximum\n"
                +"  - simpleProsody : selection stops when simple prosody coverage has reached maximum\n"
                +"-coverageConfig file : The config file for the coverage definition. \n"
                +"   Default config file is ./covDef.config.\n"                
                +"-vectorsOnDisk: if this option is given, the feature vectors are not loaded into memory during \n"
                +" the run of the program. This notably slows down the run of the program!\n"
                +"-initFile file : The file containing the coverage data needed to initialise the algorithm.\n"
                +"   Default init file is ./init.bin\n"
                +"-selectedSentences file: File containing a list of sentences selected in a previous pass \n"
                +" of the algorithm. They are added to the cover before selection starts. The sentences can be part \n"
                +" of the basename list.\n"
                +"-unwantedSentences file: File containing those sentences that are to be removed \n"
                +" from the basename list prior to selection.\n"
                +"-overallLog file : Log file for all runs of the program: date, settings and results of the current\n"
                +" run are appended to the end of the file. This file is needed if you want to analyse your results \n"
                +" with the ResultAnalyser later.\n"
                +"-selectionDir dir : the directory where all selection data is stored.\n"
                +"   Standard directory is ./selection\n" 
                +"-logCoverageDevelopment : If this option is given, the coverage development over time \n"
                +" is stored.\n"
                +"-verbose : If this option is given, there will be more output on the command line\n"
                +" during the run of the program.\n");       
    }

    /**
     * Load the basenames into memory
     *
     */
    /*
    private static void loadBasenames(){

        int numFiles ;
        try{            

            // Open the file
            BufferedReader bfr = 
                new BufferedReader( 
                        new InputStreamReader( 
                                new FileInputStream( basenameFileName ), "UTF-8" ) );

            String line = bfr.readLine();
            //check if first line contains number of entries
            if (line.matches("\\d+")){
                numFiles = Integer.parseInt(line.trim());
                basenameList = new String[numFiles];
                //the rest of the lines contain the basenames
                int index = 0;
                while ( (line = bfr.readLine()) != null ) {
                    if (line.equals(""))continue;
                    basenameList[index] = line;                
                    index++;
                }
            } else {
                //first line does not contain number of basenames
                //build a list instead of an array
                List bList = new ArrayList();
                while ( (line = bfr.readLine()) != null ) {
                    if (line.equals(""))continue;
                    bList.add(line);
                }
                //convert list to Array
                basenameList = new String[bList.size()];
                bList.toArray(basenameList);
                bList = null;
            }
            bfr.close();
        } catch (Exception e){
            e.printStackTrace();
            throw new Error("Error loading basenames");
        }

    }
    */
    
    
    private static void checkSelectedSentences(){
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
        
        try{
        System.out.println("\nChecking selected sentences whether they are wanted or not.");   
        System.out.println(" selected sentences will be saved in ./selected.log");
        PrintWriter selectedLog = new PrintWriter(new FileWriter(new File("./selected.log")));
        
        System.out.println(" unwanted sentences will be saved in ./unwanted.log");
        PrintWriter unwantedLog = new PrintWriter(new FileWriter(new File("./unwanted.log")));
        
        int sel[] = wikiToDB.getIdListOfType("dbselection", "selected=true and unwanted=false");
               
        if( sel != null){
          // checking selected sentences
          System.out.println(" Select \"y\" for marking sentence as \"wanted\" otherwise \"n\" . Press any other key to finish: \n");     
          String str;
          for(int i=0; i<sel.length; i++){
            str = wikiToDB.getSentence("dbselection", sel[i]);  
            System.out.print("id=" + sel[i] + ":  "+ str + "\n  Wanted?(y/n):");             
            
                String s = br.readLine();  
                if( s.contentEquals("n")){
                  wikiToDB.setSentenceRecord(sel[i], "unwanted", true);
                  unwantedLog.println(sel[i] + " " + str);
                } else if( s.contentEquals("y")){
                  selectedLog.println(sel[i] + " " + str);
                } else{
                  unwantedLog.close();
                  selectedLog.close();
                  break;
                }          
          }
        } else
            System.out.println("There is no selected sentences in the DB.");  
        
        } catch(Exception e){
            System.out.println(e); 
        }
        
    }
    
    /**
     * Add a list of sentences to the cover
     * Here the already selected sentences are added to the cover and the indexes removed
     * (or set to -1) in the idSentenceList
     * @param covDef the cover
     * @throws Exception
     */
    private static void addSelectedSents(CoverageDefinition covDef)throws Exception{
         
        if (verbose)
            System.out.println("\nAdding previously selected sentences ...");
        /* open the sentence file */
       // BufferedReader sentsIn = new BufferedReader(new FileReader(new File(selectedSentsFile)));
        /* read in the ids and sentences 
         * every line starts with the id and next the sentence .*/
      //  String line;
        
        
        //selectedSents = new ArrayList();
     //   selectedIdSents = new ArrayList<Integer>();
        
        //int idSentenceListSelected[] = wikiToDB.getIdListOfType("dbselection", "selected");
        int idSentenceListSelected[] = wikiToDB.getIdListOfSelectedSentences("unwanted=false");
        
        
        int id;
        byte[] vectorBuf;
        if( idSentenceListSelected != null ){
         for(int i=0; i<idSentenceListSelected.length; i++){
       // while ((line=sentsIn.readLine())!= null){
       //    if (line.equals("")) continue;
           // read in the features, get first the id from the line
         //  String words[] = line.split(" ");
         //  id = Integer.parseInt(words[0]);
            
            
            /*
            FileInputStream fis = new FileInputStream(new File(line));
            //read the first 4 bytes and combine them to get the number 
            //of feature vectors
            
            byte[] vlength = new byte[4];
            fis.read(vlength);
            int numFeatVects = (((vlength[0] & 0xff) << 24) 
                    | ((vlength[1] & 0xff) << 16) 
                    | ((vlength[2] & 0xff) << 8)
                    | (vlength[3] & 0xff));
            //read the content of the file into a byte array
            byte[] vectorBuf = new byte[4*numFeatVects];
            fis.read(vectorBuf);
            fis.close(); 
            */
            
            id = idSentenceListSelected[i];
            vectorBuf = wikiToDB.getFeatures(id); 
            
            //fill the cover set with the sentence
            covDef.updateCover(vectorBuf);
            
            //add the filename to the sentence list
            //selectedSents.add(line);
            //selectedIdSents.add(Integer.parseInt(line));
            selectedIdSents.add((Integer)id);
            
        }

        //int numSelectedSents = selectedSents.size();
        int numSelectedSents = selectedIdSents.size();
        int numRemovedSents = 0;
             
        //loop over basename array
        //for (int i=0;i<basenameList.length;i++){
        for (int i=0;i<idSentenceList.length;i++){
            //check if next basename is in the list of sentences
            /*
            if (selectedSents.contains(basenameList[i])){
                //remove the sentence also from the basename list
                basenameList[i] = null;
                numRemovedSents++;
            }
            */
            if (selectedIdSents.contains(idSentenceList[i])){
                //remove the sentence also from the idSentenceList
                if (verbose)
                  System.out.println("  Removing from idSentenceList id=" + idSentenceList[i]);
                idSentenceList[i] = -1;
                numRemovedSents++;
            }
            
            if (numSelectedSents == numRemovedSents) break;
        } 
        System.out.println("Added to cover " + idSentenceListSelected.length + " selected sentences");
        
        } else
          System.out.println("There is no already selected sentences to add to the list.");
         
    }

    /**
     * Remove unwanted sentences from the basename list
     * 
     * @throws Exception
     */
    
    private static void removeUnwantedSentences() throws Exception{
        if (verbose)
          System.out.println("\nRemoving unwanted sentences ...");
        // open the sentence file
        //BufferedReader sentsIn = new BufferedReader(new FileReader(new File(unwantedSentsFile)));
        // read in the sentences
        //String line;
        //List sents = new ArrayList();
        int idSentenceListUnwanted[] = wikiToDB.getIdListOfSelectedSentences("unwanted=true");
        
        ArrayList<Integer> unwantedIdSents = new ArrayList<Integer>();
        int id;
        if( idSentenceListUnwanted != null ){
            for(int i=0; i<idSentenceListUnwanted.length; i++){
        
           //while ((line=sentsIn.readLine())!= null){
           // if (line.equals("")) continue;           
            
           // String words[] = line.split(" ");
           // id = Integer.parseInt(words[0]);
            id = idSentenceListUnwanted[i];
            
            // marc sentence as unwanted in the locale_dbselection table
            wikiToDB.setSentenceRecord(id, "unwanted", true);
            
            //add the filename to the sentence list
            //sents.add(line);
            unwantedIdSents.add((Integer)id);
            
        }
        // remove sentences from basename list 
        //int numSelectedSents = sents.size();
        int numSelectedSents = unwantedIdSents.size();
        int numRemovedSents = 0;
        // loop over basename array
        
       // for (int i=0;i<basenameList.length;i++){
        for (int i=0;i<idSentenceList.length;i++){
            //check if next basename is in the list of sentences
            /*
            if (sents.remove(basenameList[i])){
                //remove the sentence also from the basename list
                basenameList[i] = null;
                numRemovedSents++;
            }
            */
            if (unwantedIdSents.contains(idSentenceList[i])){
                //remove the sentence also from the idSentenceList
                if (verbose)
                  System.out.println("  Removing (unwanted)from idSentenceList id=" + idSentenceList[i]);
                idSentenceList[i] = -1;
                numRemovedSents++;
            }
            if (numSelectedSents == numRemovedSents) break;
        }
           System.out.println("Removed " + idSentenceListUnwanted.length + " unwanted sentences.");        
        } else 
           System.out.println("There is no unwanted sentences to remove.");
        
    }
  
 
    
    /**
     * Print the list of selected files
     * 
     * @param filename the file to print to
     * @param selected the list of files
     */
    private static void storeResult(String filename, List selected){

        PrintWriter out;
        try{
            out = new PrintWriter(new FileWriter(new File(filename)));
        } catch (Exception e){
            e.printStackTrace();
            throw new Error("Error storing result");
        }
        StringBuffer resultBuf = new StringBuffer();
        for (int i=0;i<selected.size();i++){
            resultBuf.append(selected.get(i)+"\n");
        }
        out.print(resultBuf.toString());
        out.flush();
        out.close();
    }

}
