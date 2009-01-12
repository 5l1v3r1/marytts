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


import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.Iterator;
import java.util.Properties;
import java.util.HashMap;
import java.util.TreeMap;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.ClassLoader;


import marytts.tools.dbselection.WikipediaMarkupCleaner;

/**
 * Various functions for handling connection, inserting and querying a mysql database.
 * 
 * @author Marcela Charfuelan, Holmer Hemsen.
 */
public class DBHandler {
   
  private String locale = "en_US";   
  private Connection cn = null;
 
  private Statement  st = null;
  private ResultSet  rs = null;
  private String currentTable = null;
  private PreparedStatement psSentence = null;
  private PreparedStatement psSelectedSentence = null;
  private PreparedStatement psWord = null;
  private PreparedStatement psCleanText = null;
 
  private String cleanTextTableName = "_cleanText";
  private String wordListTableName = "_wordList";
  private String dbselectionTableName = "_dbselection";
  private String selectedSentencesTableName = "_selectedSentences";
  //public void setLocale(String str){ locale = str; }
  //public String getLocale(){ return locale; }
  
  /**
   * The constructor loads the database driver.
   * @param localVal database language. 
   */
  public DBHandler(String localeVal) {
    initDB_Driver();
    locale = localeVal;
    cleanTextTableName = locale + cleanTextTableName;
    wordListTableName = locale + wordListTableName;
    dbselectionTableName = locale + dbselectionTableName;
    selectedSentencesTableName = locale + selectedSentencesTableName;
    System.out.println("\nMysql driver loaded, set locale=" + locale);  
  } 
  
  /** Loading DB Driver */
  private void initDB_Driver()
  {  
    try {
        String driver = "com.mysql.jdbc.Driver";
        Class.forName( driver );
    }
    catch( Exception e ) {
        e.printStackTrace();
    }
  }

 
  /**
   * The <code>createDBConnection</code> method creates the database connection.
   *
   * @param host a <code>String</code> value. The database host e.g. 'localhost'.
   * @param db a <code>String</code> value. The database to connect to. 
   * @param user a <code>String</code> value. Database user that has excess to the database.
   * @param passwd a <code>String</code> value. The 'secret' password. 
   */
  public void createDBConnection(String host, String db, String user, String passwd){
    String url = "jdbc:mysql://" + host + "/" + db;
    try {
      Properties p = new Properties();
      p.put("user",user);
      p.put("password",passwd);
      p.put("database", db);
      p.put("useUnicode", "true");
      p.put("characterEncoding", "utf8");
      
      cn = DriverManager.getConnection( url, p );    
     
      st = cn.createStatement();
      
      psCleanText = cn.prepareStatement("INSERT INTO " + cleanTextTableName + " VALUES (null, ?, ?, ?, ?)");
      psWord      = cn.prepareStatement("INSERT INTO " + wordListTableName + " VALUES (null, ?, ?)");
      psSentence  = cn.prepareStatement("INSERT INTO " + dbselectionTableName + " VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?)");
      psSelectedSentence  = cn.prepareStatement("INSERT INTO " + selectedSentencesTableName + " VALUES (null, ?, ?, ?)");
      
      
    } catch (Exception e) {
        e.printStackTrace();
    } 
  }
  
  /***
   * Use MWDUMPER for extracting pages from a XML wikipedia dump file.
   * @param xmlFile xml dump file
   * @param lang locale language  
   * @param host
   * @param db
   * @param user
   * @param passwd
   * @throws Exception
   */
  public void loadPagesWithMWDumper(String xmlFile, String lang, String host, String db, String user, String passwd) throws Exception{
       
       DBHandler wikiDB = new DBHandler(lang);
       System.out.println("Using mwdumper to convert xml file into sql source file and loading text, page and revision tables into the DB.");
       System.out.println("Creating connection to DB server...");
       wikiDB.createDBConnection(host,db,user,passwd);
       
       // Before runing the mwdumper the tables text, page and revision should be deleted and created empty.
       // maybe for these we can create TEMPORARY tables  ???
       wikiDB.createEmptyWikipediaTables();
       
       // Run the mwdumper jar file xml -> sql
       //  Use these parameters for saving the output in a dump.sql source file
       /*
       String sqlDump = xmlFile + ".sql";
       String[] argsDump = new String[3];
       argsDump[0] = "--output=file:"+sqlDump;
       argsDump[1] = "--format=sql:1.5";
       argsDump[2] = xmlFile;
       */
       
       /* Use these parameters for loading direclty the pages in the DB */
       String[] argsDump = new String[3];
       argsDump[0] = "--output=mysql://" + host + "/" + db 
                     + "?user=" + user + "&password=" + passwd 
                     + "&useUnicode=true&characterEncoding=utf8";
       argsDump[1] = "--format=sql:1.5";
       argsDump[2] = xmlFile;
       
       //--- The following ClassLoader code from:
       //   http://java.sun.com/docs/books/tutorial/deployment/jar/examples/JarClassLoader.java
       // Class c = loadClass(name);                                    // this does not work (example from sun)
       // Class c = Class.forName("org.mediawiki.dumper.Dumper");                              // this works
       Class c = ClassLoader.getSystemClassLoader().loadClass("org.mediawiki.dumper.Dumper");  // this also works
       Method m = c.getMethod("main", new Class[] { argsDump.getClass() });
       m.setAccessible(true);
       int mods = m.getModifiers();
       if (m.getReturnType() != void.class || !Modifier.isStatic(mods) ||
           !Modifier.isPublic(mods)) {
           throw new NoSuchMethodException("main");
       }
       try {
           m.invoke(null, new Object[] { argsDump });
     
       } catch (IllegalAccessException e) {
           // This should not happen, as we have disabled access checks
       }
       
       // Now I need to add/change the prefix locale to the table names
       // Renaming tables
       wikiDB.addLocalePrefixToWikipediaTables();  // this change the name of already created and loaded tables
       wikiDB.closeDBConnection();
       
  }
  
  
  
  
  public void createDataBaseSelectionTable() {
      String dbselection = "CREATE TABLE " + dbselectionTableName + " ( id INT NOT NULL AUTO_INCREMENT, " +                                                       
                                                       "sentence MEDIUMBLOB NOT NULL, " +
                                                       "features BLOB, " +
                                                       "reliable BOOLEAN, " +
                                                       "unknownWords BOOLEAN, " +
                                                       "strangeSymbols BOOLEAN, " +
                                                       "selected BOOLEAN, " +
                                                       "unwanted BOOLEAN, " +
                                                       "cleanText_id INT UNSIGNED NOT NULL, " +   // the cleanText id where this sentence comes from
                                                       "primary key(id)) CHARACTER SET utf8;";
      String str;
      boolean dbExist = false;
      // if database does not exist create it    
      System.out.println("Checking if " + dbselectionTableName + " already exist.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      
      try { 
          while( rs.next() ) {
            str = rs.getString(1);
            if( str.contentEquals(dbselectionTableName) ){
               System.out.println("TABLE = " + str + " already exist.");
               dbExist = true;
            }
          }
          if( !dbExist ) {
              boolean res = st.execute( dbselection );
              System.out.println("TABLE = " + dbselectionTableName + " succesfully created.");   
          }
      } catch (SQLException e) {
          e.printStackTrace();
      } 
  }
  
  
  public void createSelectedSentencesTable() {
      String selected = "CREATE TABLE " + selectedSentencesTableName + " ( id INT NOT NULL AUTO_INCREMENT, " +                                                       
                                                       "sentence MEDIUMBLOB NOT NULL, " +
                                                       "unwanted BOOLEAN, " +
                                                       "dbselection_id INT UNSIGNED NOT NULL, " +   // the dbselection id where this sentence comes from
                                                       "primary key(id)) CHARACTER SET utf8;";
      String str;
      boolean dbExist = false;
      // if database does not exist create it    
      System.out.println("Checking if " + selectedSentencesTableName + " already exist.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      
      try { 
          while( rs.next() ) {
            str = rs.getString(1);
            if( str.contentEquals(selectedSentencesTableName) ){
               dbExist = true;
            }
          }
          if(dbExist==true){
              System.out.println("TABLE = " + selectedSentencesTableName + " already exist deleting.");  
              boolean res0 = st.execute( "DROP TABLE " + selectedSentencesTableName + ";" );  
          }
          
          boolean res = st.execute( selected );
          System.out.println("TABLE = " + selectedSentencesTableName + " succesfully created.");   
          
      } catch (SQLException e) {
          e.printStackTrace();
      } 
  }
  
  
  /***
   * This function creates text, page and revision tables loading them from text files.
   * @param textFile
   * @param pageFile
   * @param revisionFile
   */
  public void createAndLoadWikipediaTables(String textFile, String pageFile, String revisionFile) {
      
      String createTextTable = "CREATE TABLE " + locale + "_text (" +
              " old_id int UNSIGNED NOT NULL AUTO_INCREMENT," +
              " old_text mediumblob NOT NULL," +
              " old_flags tinyblob NOT NULL," +
              " PRIMARY KEY old_id (old_id)" +
              " ) MAX_ROWS=250000 AVG_ROW_LENGTH=10240;";
      
      String createPageTable = "CREATE TABLE " + locale + "_page (" +
            "page_id int UNSIGNED NOT NULL AUTO_INCREMENT," +
            "page_namespace int(11) NOT NULL," +
            "page_title varchar(255) NOT NULL," +
            "page_restrictions tinyblob NOT NULL," +
            "page_counter bigint(20) unsigned NOT NULL," +
            "page_is_redirect tinyint(3) unsigned NOT NULL," +
            "page_is_new tinyint(3) unsigned NOT NULL," +
            "page_random double unsigned NOT NULL," +
            "page_touched binary(14) NOT NULL," +
            "page_latest int(10) unsigned NOT NULL," +
            "page_len int(10) unsigned NOT NULL," +
            "PRIMARY KEY page_id (page_id)," +
            "KEY page_namespace (page_namespace)," +
            "KEY page_random (page_random)," +
            "KEY page_len (page_len) ) MAX_ROWS=250000 AVG_ROW_LENGTH=10240; ";
      
      String createRevisionTable = "CREATE TABLE " + locale + "_revision (" +
            "rev_id int UNSIGNED NOT NULL AUTO_INCREMENT," +
            "rev_page int(10) unsigned NOT NULL," +
            "rev_text_id int(10) unsigned NOT NULL," +
            "rev_comment tinyblob NOT NULL," +
            "rev_user int(10) unsigned NOT NULL," +
            "rev_user_text varchar(255) NOT NULL, " +
            "rev_timestamp binary(14) NOT NULL, " +
            "rev_minor_edit tinyint(3) unsigned NOT NULL," +
            " rev_deleted tinyint(3) unsigned NOT NULL," +
            "rev_len int(10) unsigned NULL," +
            "rev_parent_id int(10) unsigned NULL," +
            "KEY rev_user (rev_user),KEY rev_user_text (rev_user_text)," +
            "KEY rev_timestamp (rev_timestamp)," +
            "PRIMARY KEY rev_id (rev_id)) MAX_ROWS=250000 AVG_ROW_LENGTH=10240;";
      
      // If database does not exist create it, if it exists delete it and create an empty one.      
      //System.out.println("Checking if the TABLE=text already exist.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      boolean resText=false, resPage=false, resRevision=false;
      try { 
         
          while( rs.next() ) {
            String str = rs.getString(1);
            if( str.contentEquals(locale+"_text") )
               resText=true;
            else if( str.contentEquals(locale+"_page") )
               resPage=true;
            else if( str.contentEquals(locale+"_revision") )
               resRevision=true;
            
          } 
          if(resText==true){
            System.out.println("TABLE = " + locale + "_text already exist deleting.");  
            boolean res0 = st.execute( "DROP TABLE " + locale + "_text;" );  
          }
          if(resPage==true){
              System.out.println("TABLE = " + locale + "_page already exist deleting.");  
              boolean res0 = st.execute( "DROP TABLE " + locale + "_page;" );  
          }
          if(resRevision==true){
              System.out.println("TABLE = " + locale + "_revision already exist deleting.");  
              boolean res0 = st.execute( "DROP TABLE " + locale + "_revision;" );  
          }
          
          boolean res1;
          int res2;
          // creating TABLE=text
          //System.out.println("\nCreating table:" + createTextTable);
          System.out.println("\nCreating table:" + locale + "_text");
          res1 = st.execute( createTextTable );         
          System.out.println("Loading sql file: " + textFile);
          res2 = st.executeUpdate("LOAD DATA LOCAL INFILE '" + textFile + "' into table " + locale + "_text;");
          System.out.println("TABLE = " + locale + "_text succesfully created.");   
          
          // creating TABLE=page
          //System.out.println("\nCreating table:" + createPageTable);
          System.out.println("\nCreating table:" + locale + "_page");
          res1 = st.execute( createPageTable );         
          System.out.println("Loading sql file: " + pageFile);
          res2 = st.executeUpdate("LOAD DATA LOCAL INFILE '" + pageFile + "' into table " + locale + "_page;");
          System.out.println("TABLE = " + locale + "_page succesfully created.");  
          
          // creating TABLE=revision
          //System.out.println("\n\nCreating table:" + createRevisionTable);
          System.out.println("\nCreating table:" + locale + "_revision");
          res1 = st.execute( createRevisionTable );         
          System.out.println("Loading sql file: " + revisionFile);
          System.out.println("SOURCE " + revisionFile + ";" );
          res2 = st.executeUpdate("LOAD DATA LOCAL INFILE '" + revisionFile + "' into table " + locale + "_revision;");
          System.out.println("TABLE = " + locale + "_revision succesfully created." );   
                 
          
      } catch (SQLException e) {
          e.printStackTrace();
      } 
  }
  

  
  
  /***
   * This function creates text, page and revision tables (without locale prefix).
   * If the tables already exist it will delete them.
   */
  public void createEmptyWikipediaTables() {
      System.out.println("Creating empty wikipedia tables");
      String createTextTable = "CREATE TABLE text (" +
              " old_id int UNSIGNED NOT NULL AUTO_INCREMENT," +
              " old_text mediumblob NOT NULL," +
              " old_flags tinyblob NOT NULL," +
              " PRIMARY KEY old_id (old_id)" +
              " ) MAX_ROWS=250000 AVG_ROW_LENGTH=10240 CHARACTER SET utf8;;";
      
      String createPageTable = "CREATE TABLE page (" +
            "page_id int UNSIGNED NOT NULL AUTO_INCREMENT," +
            "page_namespace int(11) NOT NULL," +
            "page_title varchar(255) NOT NULL," +
            "page_restrictions tinyblob NOT NULL," +
            "page_counter bigint(20) unsigned NOT NULL," +
            "page_is_redirect tinyint(3) unsigned NOT NULL," +
            "page_is_new tinyint(3) unsigned NOT NULL," +
            "page_random double unsigned NOT NULL," +
            "page_touched binary(14) NOT NULL," +
            "page_latest int(10) unsigned NOT NULL," +
            "page_len int(10) unsigned NOT NULL," +
            "PRIMARY KEY page_id (page_id)," +
            "KEY page_namespace (page_namespace)," +
            "KEY page_random (page_random)," +
            "KEY page_len (page_len) ) MAX_ROWS=250000 AVG_ROW_LENGTH=10240 CHARACTER SET utf8;; ";
      
      String createRevisionTable = "CREATE TABLE revision (" +
            "rev_id int UNSIGNED NOT NULL AUTO_INCREMENT," +
            "rev_page int(10) unsigned NOT NULL," +
            "rev_text_id int(10) unsigned NOT NULL," +
            "rev_comment tinyblob NOT NULL," +
            "rev_user int(10) unsigned NOT NULL," +
            "rev_user_text varchar(255) NOT NULL, " +
            "rev_timestamp binary(14) NOT NULL, " +
            "rev_minor_edit tinyint(3) unsigned NOT NULL," +
            " rev_deleted tinyint(3) unsigned NOT NULL," +
            "rev_len int(10) unsigned NULL," +
            "rev_parent_id int(10) unsigned NULL," +
            "KEY rev_user (rev_user),KEY rev_user_text (rev_user_text)," +
            "KEY rev_timestamp (rev_timestamp)," +
            "PRIMARY KEY rev_id (rev_id)) MAX_ROWS=250000 AVG_ROW_LENGTH=10240 CHARACTER SET utf8;;";
      
      // If database does not exist create it, if it exists delete it and create an empty one.      
      //System.out.println("Checking if the TABLE=text already exist.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      boolean resText=false, resPage=false, resRevision=false;
      try { 
         
          while( rs.next() ) {
            String str = rs.getString(1);
            if( str.contentEquals("text") )
               resText=true;
            else if( str.contentEquals("page") )
               resPage=true;
            else if( str.contentEquals("revision") )
               resRevision=true;
            
          } 
          boolean res0;
          if(resText==true){
            System.out.println("  TABLE = text already exist deleting.");  
            res0 = st.execute( "DROP TABLE text;" );  
          }
          if(resPage==true){
              System.out.println("  TABLE = page already exist deleting.");  
              res0 = st.execute( "DROP TABLE page;" );  
          }
          if(resRevision==true){
              System.out.println("  TABLE = revision already exist deleting.");  
              res0 = st.execute( "DROP TABLE revision;" );  
          }
          
          boolean res1;
          int res2;
          // creating TABLE=text,page and revision
          System.out.println("  Creating table: text");
          res1 = st.execute( createTextTable );  
          System.out.println("  Creating table: page");
          res1 = st.execute( createPageTable );
          System.out.println("  Creating table: revision");
          res1 = st.execute( createRevisionTable );           
          
      } catch (SQLException e) {
          e.printStackTrace();
      } 
  }
 
  /****
   * Rename the Wikipedia tables adding the prefix locale: 
   *  locale_text
   *  locale_page and 
   *  locale_revision.
   *  if any of the locale_* tables exist will be deleted before renaming the table.
   */
  public void addLocalePrefixToWikipediaTables() {
        
     System.out.println("Deleting already used wikipedia tables and adding local prefix to new ones.");
     try {
         rs = st.executeQuery("SHOW TABLES;");
     } catch (Exception e) {
         e.printStackTrace();
     }
     boolean resText=false, resPage=false, resRevision=false;
     boolean resLocaleText=false, resLocalePage=false, resLocaleRevision=false;
     try { 
        
         while( rs.next() ) {
           String str = rs.getString(1);
           if( str.contentEquals("text") )
              resText=true;
           else if( str.contentEquals(locale + "_text") )
               resLocaleText=true;
           else if( str.contentEquals("page") )
              resPage=true;
           else if( str.contentEquals(locale + "_page") )
               resLocalePage=true;
           else if( str.contentEquals("revision") )
              resRevision=true;
           else if( str.contentEquals(locale + "_revision") )
               resLocaleRevision=true;
         } 
         if(resLocaleText==true){
             System.out.println("  Deleting TABLE = " + locale + "_text.");  
             boolean res0 = st.execute( "DROP TABLE " + locale + "_text;" );  
           }
           if(resLocalePage==true){
               System.out.println("  Deleting TABLE = " + locale + "_page.");  
               boolean res0 = st.execute( "DROP TABLE " + locale + "_page;" );  
           }
           if(resLocaleRevision==true){
               System.out.println("  Deleting TABLE = " + locale + "_revision.");  
               boolean res0 = st.execute( "DROP TABLE " + locale + "_revision;" );  
           }   
           
         if(resText==true){
           System.out.println("  RENAME TABLE = text TO " + locale + "_text.");  
           boolean res0 = st.execute( "RENAME TABLE text TO " + locale + "_text;" );  
         }
         if(resPage==true){
             System.out.println("  RENAME TABLE = page TO " + locale + "_page.");  
             boolean res0 = st.execute( "RENAME TABLE page TO " + locale + "_page;" );  
         }
         if(resRevision==true){
             System.out.println("  RENAME TABLE = revision TO " + locale + "_revision.");  
             boolean res0 = st.execute( "RENAME TABLE revision TO " + locale + "_revision;" );  
         }       
         
     } catch (SQLException e) {
         e.printStackTrace();
     } 
 }
  
  
   /****
    * Delete the Wikipedia tables: text, page and revision tables.
    *
    */
   public void deleteWikipediaTables() {
         
      System.out.println("Deleting already used wikipedia tables.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      boolean resText=false, resPage=false, resRevision=false;
      try { 
         
          while( rs.next() ) {
            String str = rs.getString(1);
            if( str.contentEquals(locale+"_text") )
               resText=true;
            else if( str.contentEquals(locale+"_page") )
               resPage=true;
            else if( str.contentEquals(locale+"_revision") )
               resRevision=true;
            
          } 
          if(resText==true){
            System.out.println("  Deleting TABLE = " + locale + "_text.");  
            boolean res0 = st.execute( "DROP TABLE " + locale + "_text;" );  
          }
          if(resPage==true){
              System.out.println("  Deleting TABLE = " + locale + "_page.");  
              boolean res0 = st.execute( "DROP TABLE " + locale + "_page;" );  
          }
          if(resRevision==true){
              System.out.println("  Deleting TABLE = " + locale + "_revision.");  
              boolean res0 = st.execute( "DROP TABLE " + locale + "_revision;" );  
          }       
          
      } catch (SQLException e) {
          e.printStackTrace();
      } 
  }

  public boolean checkWikipediaTables() {
      // wiki must be already created
      
      // If database does not exist create it, if it exists delete it and create an empty one.      
      System.out.println("Checking if the TABLE=" + locale + "_text already exist.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      boolean resText=false, resPage=false, resRevision=false;
      try { 
         
          while( rs.next() ) {
            String str = rs.getString(1);
            if( str.contentEquals(locale+"_text") )
               resText=true;
            else if( str.contentEquals(locale+"_page") )
               resPage=true;
            else if( str.contentEquals(locale+"_revision") )
               resRevision=true;
            
          } 
          if(resText)
            System.out.println("TABLE =" + locale + "_text already exist.");          
          if(resPage)
            System.out.println("TABLE =" + locale + "_page already exist.");  
          if(resRevision)
            System.out.println("TABLE =" + locale + "_revision already exist.");  
                  
          
      } catch (SQLException e) {
          e.printStackTrace();
      } 
      
      if(resText && resPage && resRevision)
        return true;
      else
        return false;
      
  }

  
  public void createWikipediaCleanTextTable() {
      // wiki must be already created
      // String creteWiki = "CREATE DATABASE wiki;";
      String createCleanTextTable = "CREATE TABLE "+ cleanTextTableName +" (" +
              " id int UNSIGNED NOT NULL AUTO_INCREMENT," +
              " cleanText MEDIUMBLOB NOT NULL," +
              " processed BOOLEAN, " +
              " page_id int UNSIGNED NOT NULL, " +
              " text_id int UNSIGNED NOT NULL, " +
              " PRIMARY KEY id (id)" +
              " ) MAX_ROWS=250000 AVG_ROW_LENGTH=10240 CHARACTER SET utf8;";
           
      // If database does not exist create it, if it exists delete it and create an empty one.      
      System.out.println("Checking if the TABLE=" + cleanTextTableName + " already exist.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      boolean resText=false;
      try { 
         
          while( rs.next() ) {
            String str = rs.getString(1);
            if( str.contentEquals(cleanTextTableName) )
               resText=true;
          } 
          if(resText==true){
            System.out.println("TABLE = " + cleanTextTableName + " already exist deleting.");  
            boolean res0 = st.execute( "DROP TABLE " + cleanTextTableName + ";" );  
          }
          
          boolean res1;
          int res2;
          // creating TABLE=cleanText
          //System.out.println("\nCreating table:" + createCleanTextTable);
          System.out.println("\nCreating table:" + cleanTextTableName);
          res1 = st.execute( createCleanTextTable );         
          System.out.println("TABLE = " + cleanTextTableName + " succesfully created.");
         
           
      } catch (SQLException e) {
          e.printStackTrace();
      } 
  }

  /***
   * 
   * @return true if TABLE=tableName exist.
   */
  public boolean tableExist(String tableName) {
      // wiki must be already created
           
      // If database does not exist create it, if it exists delete it and create an empty one.      
      System.out.println("Checking if the TABLE=" + tableName + " exist.");
      try {
          rs = st.executeQuery("SHOW TABLES;");
      } catch (Exception e) {
          e.printStackTrace();
      }
      boolean res=false;
      try {        
          while( rs.next() ) {
            String str = rs.getString(1);
            if( str.contentEquals(tableName) )
               res=true;
          } 
          
      } catch (SQLException e) {
          e.printStackTrace();
      } 
      
      return res;
      
  }

 
  /***
   * 
   * @param field
   * @param table
   * @return
   */
  public String[] getIds(String field, String table) {
      int num, i, j;
      String idSet[]=null;
      
      String str = queryTable("SELECT count("+ field + ") FROM " + table + ";");  
      num = Integer.parseInt(str);
      idSet = new String[num];
      
      try {
          rs = st.executeQuery("SELECT " + field + " FROM " + table + ";"); 
      } catch (Exception e) {
          e.printStackTrace();
      }
      try { 
          i=0;
          while( rs.next() ) {
            idSet[i] = rs.getString(1);
            i++;
          }
      } catch (SQLException e) {
          e.printStackTrace();
      } 
      
      return idSet;
  }
  
  /***
   * This function will select just the unprocessed cleanText records.
   * @param field
   * @param table
   * @return
   */
  public String[] getUnprocessedTextIds() {
      int num, i, j;
      String idSet[]=null;
      
      String str = queryTable("select count(id) from " + cleanTextTableName + " where processed=false;");  
      num = Integer.parseInt(str);
      idSet = new String[num];
      
      try {
          rs = st.executeQuery("select id from " + cleanTextTableName +" where processed=false;"); 
      } catch (Exception e) {
          e.printStackTrace();
      }
      try { 
          i=0;
          while( rs.next() ) {
            idSet[i] = rs.getString(1);
            i++;
          }
      } catch (SQLException e) {
          e.printStackTrace();
      } 
      
      return idSet;
  }
  
  
  public void setDBTable(String table){
    currentTable = table;
  }

  public void setWordListTable(String table){ 
      wordListTableName = table;
  }
  
  public void insertCleanText(String text, String page_id, String text_id){
      //System.out.println("inserting in cleanText: ");
      byte cleanText[]=null;
      
      try {
        cleanText = text.getBytes("UTF8");
      } catch (Exception e) {  // UnsupportedEncodedException
        e.printStackTrace();
      } 
      
      try { 
        //ps = cn.prepareStatement("INSERT INTO cleanText VALUES (null, ?, ?, ?, ?)");
        if(cleanText != null){
            psCleanText.setBytes(1, cleanText);
            psCleanText.setBoolean(2, false);   // it will be true after processed by the FeatureMaker
            psCleanText.setInt(3, Integer.parseInt(page_id));
            psCleanText.setInt(4, Integer.parseInt(text_id));
            psCleanText.execute();
            psCleanText.clearParameters();
        } else
           System.out.println("WARNING: can not insert in " + cleanTextTableName + ": " + text); 
        
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  
  /****
   * Insert processed sentence in dbselection
   * @param sentence text of the sentence.
   * @param features features if sentences is reliable.
   * @param reliable true/false.
   * @param unknownWords true/false.
   * @param strangeSymbols true/false.
   * @param cleanText_id the id of the cleanText this sentence comes from.
   */
  public void insertSentence(String sentence, byte features[], boolean reliable, boolean unknownWords, boolean strangeSymbols, int cleanText_id){
      
    byte strByte[]=null;  
    try {
       strByte = sentence.getBytes("UTF8");
    } catch (Exception e) {  // UnsupportedEncodedException
          e.printStackTrace();
    } 
    
    try { 
      // INSERT INTO dbselection VALUES (id, sentence, features, realiable, unknownWords, strangeSymbols, selected, unwanted, cleanText_id)
      //ps = cn.prepareStatement("INSERT INTO dbselection VALUES (null, ?, ?, ?, ?, ?, ?,?)");
      
        psSentence.setBytes(1, strByte);
        psSentence.setBytes(2, features);
        psSentence.setBoolean(3, reliable);
        psSentence.setBoolean(4, unknownWords);
        psSentence.setBoolean(5, strangeSymbols);
        psSentence.setBoolean(6, false);
        psSentence.setBoolean(7, false);
        psSentence.setInt(8, cleanText_id);
        psSentence.execute();
      
        psSentence.clearParameters();
      
      
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
  
  /****
   * Insert processed sentence in dbselection
   * @param sentence text of the sentence.
   * @param features features if sentences is reliable.
   * @param reliable true/false.
   * @param unknownWords true/false.
   * @param strangeSymbols true/false.
   * @param cleanText_id the id of the cleanText this sentence comes from.
   */
  public void insertSelectedSentence(int dbselection_id, boolean unwanted){
  
    String dbQuery = "Select sentence FROM " + dbselectionTableName + " WHERE id=" + dbselection_id;
    byte[] sentenceBytes=null;
      
    try {
      // First get the sentence
       sentenceBytes = queryTableByte(dbQuery);    
        
      // INSERT INTO dbselection VALUES (id, sentence, unwanted, dbselection_id)
      //ps = cn.prepareStatement("INSERT INTO dbselection VALUES (null, ?, ?, ?)");
    
        psSelectedSentence.setBytes(1, sentenceBytes);
        psSelectedSentence.setBoolean(2, unwanted);
        psSelectedSentence.setInt(3, dbselection_id);
        psSelectedSentence.execute();
      
        psSelectedSentence.clearParameters();
      
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  /****
   * Creates a wordList table, if already exists deletes it and creates a new to insert
   * current wordList.
   * 
   */
  public void insertWordList(HashMap<String, Integer> wordList){
    String word;
    Integer value; 
    boolean res;
    byte wordByte[];
    
    String wordListTable = "CREATE TABLE " + wordListTableName + " ( id INT NOT NULL AUTO_INCREMENT, " +                                                       
                                              "word TINYBLOB NOT NULL, " +
                                              "frequency INT UNSIGNED NOT NULL, " +
                                              "primary key(id)) CHARACTER SET utf8;";
    
    try { 
        System.out.println("Inserting wordList in DB...");
        // if wordList table already exist it should be deleted before inserting this list
        if( tableExist(wordListTableName) ){
          res = st.execute( "DROP TABLE " + wordListTableName + ";" );
          res = st.execute( wordListTable );
        } else
          res = st.execute( wordListTable );   
        
        //psWord = cn.prepareStatement("INSERT INTO wordList VALUES (null, ?, ?)");   
        try {
          Iterator iteratorSorted = wordList.keySet().iterator();
          while (iteratorSorted.hasNext()) {
            word = iteratorSorted.next().toString();
            value = wordList.get(word);
            wordByte=null;  
            wordByte = word.getBytes("UTF8");
            psWord.setBytes(1, wordByte);
            psWord.setInt(2, value);
            psWord.execute();
            psWord.clearParameters();
          } 
        } catch (Exception e) {  // UnsupportedEncodedException
            e.printStackTrace();
      } 
        System.out.println("Inserted new words in " + wordListTableName + " table."); 
        
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
  
  
  public void closeDBConnection(){
    try {
        cn.close();    
    } catch (SQLException e) {
        e.printStackTrace();
    }  
  }
  
  
  public int getNumberOfReliableSentences() {
      String dbQuery = "SELECT count(sentence) FROM " + dbselectionTableName + " where reliable=true;";
      String str = queryTable(dbQuery);
      return Integer.parseInt(str);
  }
  

  /***
   * Get a list of id's
   * @param table cleanText, wordList, dbselection, selectedSenteces (no need to add locale)
   * @param type reliable, unknownWords, strangeSymbols, selected, unwanted or type=null
   *             for querying without type constraint.
   * @return int array
   */
  public int[] getIdListOfType(String table, String type) {
      int num, i, j;
      int idSet[]=null;
      String getNum, getIds;
      if(type!=null){
        getNum =  "SELECT count(id) FROM " + locale + "_" + table + " where " + type + "=true;";
        getIds =  "SELECT id FROM " + locale + "_" + table +  " where " + type + "=true;";
      } else {
        getNum =  "SELECT count(id) FROM " + locale + "_" + table + ";";
        getIds =  "SELECT id FROM " + locale + "_" + table + ";";
      }
      
      String str = queryTable(getNum);
      num = Integer.parseInt(str);
      idSet = new int[num];
      
      try {
          rs = st.executeQuery(getIds); 
      } catch (Exception e) {
          e.printStackTrace();
      }
      try { 
          i=0;
          while( rs.next() ) {
            idSet[i] = rs.getInt(1);
            i++;
          }
      } catch (SQLException e) {
          e.printStackTrace();
      } 
      
      return idSet;
  }
  
  /***
   * Get number of words in the wordList table.
   * @return int number of words.
   * @param maxFreq max frequency of a word to be considered in the list, if maxFrequency=0
   *                 it will retrieve all the words with frequency>=1.
   */
  public int getNumberOfWords(int maxFrequency){
      String where="";
      if(maxFrequency>0)
          where = "where frequency > " + maxFrequency;
      String dbQuery = "SELECT count(word) FROM " + wordListTableName + " " + where + ";";
      String str = queryTable(dbQuery);
      return Integer.parseInt(str);  
  }
  
  /****
   * 
   * @return the word list in a HashMap
   * @param numWords max number of words to retrieve, if numWords=0 then it will retrieve
   *                 all the words in the list in descending order of frequency.
   * @param maxFreq max frequency of a word to be considered in the list, if maxFrequency=0
   *                 it will retrieve all the words with frequency>=1.
   */
  public HashMap<String, Integer> getMostFrequentWords(int numWords, int maxFrequency) {

      HashMap<String, Integer> wordList;
      String dbQuery, where="", word;
      int initialCapacity = 200000;  
      byte wordBytes[];
      
      if(maxFrequency>0)
        where = "where frequency > " + maxFrequency;
      
      if(numWords>0){
        dbQuery = "SELECT word,frequency FROM " + wordListTableName + " " + where + " order by frequency desc limit " + numWords +";";
        wordList = new HashMap<String, Integer>(numWords);
      }
      else {  
        dbQuery = "SELECT word,frequency FROM " + wordListTableName + " " + where + " order by frequency desc";
        wordList = new HashMap<String, Integer>(initialCapacity);
      }
      
      try {
          rs = st.executeQuery(dbQuery); 
      } catch (Exception e) {
          e.printStackTrace();
      }
      try { 
          while( rs.next() ) {
            wordBytes=rs.getBytes(1);
            word = new String(wordBytes, "UTF8");  
            wordList.put(word, new Integer(rs.getInt(2)));
          }
      } catch (SQLException e) {
          e.printStackTrace();
      } catch (Exception e){   // catch unsupported encoding exception
          e.printStackTrace();
      } 
      
      return wordList;
  }
  
  /****
   * 
   * @param fileName file to write the list
   * @param order word or frequency
   * @param numWords max number of words, if numWords=0 then it will retrieve all the
   *                 words in the list.
   * @param maxFreq max frequency of a word to be considered in the list, if maxFrequency=0
   *                 it will retrieve all the words with frequency>=1.
   */
  public void printWordList(String fileName, String order, int numWords, int maxFrequency) {
      PrintWriter pw;
      String dbQuery, where="";
      String orderBy;
      byte wordBytes[];
      String word;
      
      if(maxFrequency>0)
          where = "where frequency > " + maxFrequency;
      
      if(order.contentEquals("word"))
        orderBy = "word asc";
      else
        orderBy = "frequency desc";  
      
      if(numWords>0)
        dbQuery = "SELECT word,frequency from " + wordListTableName + " " + where + " order by " + orderBy + " limit " + numWords +";"; 
      else   
        dbQuery = "SELECT word,frequency from " + wordListTableName + " " + where + " order by " + orderBy;
          
      
      try {
          rs = st.executeQuery(dbQuery); 
      } catch (Exception e) {
          e.printStackTrace();
      }
      try { 
          pw = new PrintWriter(new FileWriter(new File(fileName)));
          wordBytes=null;
          while( rs.next() ) {
            wordBytes=rs.getBytes(1);
            word = new String(wordBytes, "UTF8");  
            pw.println(word + " " + rs.getInt(2));
          }
          pw.close();
          
      } catch (SQLException e) {
          e.printStackTrace();
      } catch (Exception e){   // catch unsupported encoding exception
          e.printStackTrace();
      } 
      System.out.println(wordListTableName + " printed in file: " + fileName + " ordered by " + order);
      
  }
  
  /***
   * Get a sentence from dbselection table or selectedSentences table
   * @param table dbselection, selectedSenteces (no need to add locale)
   * @return String sentence
   */
  public String getSentence(String table, int id) {
      String sentence="";
      String dbQuery = "Select sentence FROM " + locale + "_" + table + " WHERE id=" + id;
      byte[] sentenceBytes=null;
      
      sentenceBytes = queryTableByte(dbQuery); 
      try {
        sentence = new String(sentenceBytes, "UTF8");
        //System.out.println("  TEXT: " + text);
      } catch (Exception e) {  // UnsupportedEncodedException
           e.printStackTrace();
      } 
      
      return sentence;      
  }
  
  // Firts filtering:
  // get first the page_title and check if it is not Image: or  Wikipedia:Votes_for_deletion/
  // maybe we can check also the length
  public String getTextFromWikiPage(String id, int minPageLength, StringBuffer old_id, PrintWriter pw) {
      String pageTitle, pageLen, dbQuery, textId, text=null;
      byte[] textBytes=null;
      int len;
      
      dbQuery = "Select page_title FROM " + locale + "_page WHERE page_id=" + id;
      pageTitle = queryTable(dbQuery);
      
      dbQuery = "Select page_len FROM " + locale + "_page WHERE page_id=" + id;
      pageLen = queryTable(dbQuery);
      len = Integer.parseInt(pageLen);
      
      if(len < minPageLength || pageTitle.contains("Wikipedia:") 
                             || pageTitle.contains("Image:")
                             || pageTitle.contains("Template:")
                             || pageTitle.contains("Category:")
                             || pageTitle.contains("List_of_")){
        //System.out.println("PAGE NOT USED page title=" + pageTitle + " Len=" + len);       
        /*
        dbQuery = "select rev_text_id from revision where rev_page=" + id;
        textId = queryTable(dbQuery);
        dbQuery = "select old_text from text where old_id=" + textId;
        text = queryTable(dbQuery);
        System.out.println("TEXT: " + text);
        */
      }
      else {
        //System.out.println("PAGE page_id=" + id + " PAGE SELECTED page title=" + pageTitle + " Len=" + len);
        if(pw!=null)
          pw.println("\nSELECTED PAGE TITLE=" + pageTitle + " Len=" + len);
        
        dbQuery = "select rev_text_id from " + locale + "_revision where rev_page=" + id;
        textId = queryTable(dbQuery);
        old_id.delete(0, old_id.length());
        old_id.insert(0, textId);
        
        dbQuery = "select old_text from " + locale + "_text where old_id=" + textId;        
        textBytes = queryTableByte(dbQuery); 
        try {
          text = new String(textBytes, "UTF8");
          //System.out.println("  TEXT: " + text);
        } catch (Exception e) {  // UnsupportedEncodedException
             e.printStackTrace();
        } 
        
      }
      return text;   
  }
  
  public String getCleanText(String id){
      String dbQuery, text=null;
      byte[] textBytes=null;
             
      dbQuery = " select cleanText from " + cleanTextTableName + " where id=" + id;
      textBytes = queryTableByte(dbQuery);
      
      try {
          text = new String(textBytes, "UTF8");
          //System.out.println("  TEXT: " + text);
      } catch (Exception e) {  // UnsupportedEncodedException
        e.printStackTrace();
      } 
      // once retrieved the text record mark it as processed
      updateTable("UPDATE " + cleanTextTableName + " SET processed=true WHERE id="+id);   
     
      return text;     
  }
  
  /***
   * Set a sentence record field as true/false in dbselection table.
   * @param id
   * @param field  reliable, unknownWords, strangeSymbols, selected or unwanted = true/false
   * @param fieldvalue true/false (as string)
   */
  public void setSentenceRecord(int id, String field, boolean fieldValue){
    String bval;  
    if(fieldValue)
      bval = "true";
    else
      bval = "false";   
        
    updateTable("UPDATE " + dbselectionTableName + " SET " + field + "=" + bval + " WHERE id=" + id);  
      
  }
  
  public String getFeaturesFromTable(int id, String table) {
      String dbQuery = "Select features FROM " + table + " WHERE id=" + id;
      return queryTable(dbQuery);
      
  }
  
  private boolean updateTable(String sql)
  {
      String str = "";
      boolean res=false;
      
      try {
          res = st.execute(sql);
      } catch (Exception e) {
          e.printStackTrace();
      } 
      
      return res;
      
  }

  private String queryTable(String dbQuery)
  {
      String str = "";
      //String dbQuery = "Select * FROM " + currentTable;
      //System.out.println("querying: " + dbQuery);
      try {
          rs = st.executeQuery( dbQuery );
      } catch (Exception e) {
          e.printStackTrace();
      } 

      try {   
          while( rs.next() ) {
              //String url = rs.getString(2);
              //str = rs.getString(field);
              str = rs.getString(1);
          }
      } catch (SQLException e) {
          e.printStackTrace();
      } 
      return str;
      
  }
  
  private byte[] queryTableByte(String dbQuery)
  {
      byte strBytes[]=null;
      //String dbQuery = "Select * FROM " + currentTable;
      //System.out.println("querying: " + dbQuery);
      try {
          rs = st.executeQuery( dbQuery );
      } catch (Exception e) {
          e.printStackTrace();
      } 

      try {   
          while( rs.next() ) {
              //String url = rs.getString(2);
              //str = rs.getString(field);
              //str = rs.getString(1);
              strBytes = rs.getBytes(1);
          }
      } catch (Exception e) {
          e.printStackTrace();
      } 
      return strBytes;
      
  }

  public byte[] getFeatures(int id)
  {
      byte[] fea = null;
      String dbQuery = "Select features FROM " + dbselectionTableName + " WHERE id=" + id;
      //System.out.println("querying: " + dbQuery);
      try {
          rs = st.executeQuery( dbQuery );
      } catch (Exception e) {
          e.printStackTrace();
      } 

      try {   
          while( rs.next() ) {
              fea = rs.getBytes(1);
          }
      } catch (SQLException e) {
          e.printStackTrace();
      } 
      return fea;
      
  }

  
  /** The following characteres should be escaped:
   * \0  An ASCII 0 (NUL) character.
   * \'  A single quote (“'”) character.
   * \"  A double quote (“"”) character.
   */
  public String mysqlEscapeCharacters(String str){
    
    str = str.replace("\0", "");
    str = str.replace("'", "\'");
    str = str.replace("\"", "\\\"");  
    
    return str;  
  }

  public static void main(String[] args) throws Exception{
      
      //Put sentences and features in the database.
      DBHandler wikiToDB = new DBHandler("en_US");

      wikiToDB.createDBConnection("localhost","wiki","marcela","wiki123");
      
      int numWords = wikiToDB.getNumberOfWords(0);
      
      wikiToDB.closeDBConnection();
 
      
      wikiToDB.createDataBaseSelectionTable();
      
      wikiToDB.getIdListOfType("dbselection", "reliable");
      
      int num = wikiToDB.getNumberOfReliableSentences();
      
      String feas = wikiToDB.getFeaturesFromTable(1, "dbselection");
      
      
      byte [] tmp = new byte[4];
      tmp[0] = 10;
      tmp[1] = 20;
      tmp[2] = 30;
      tmp[3] = 40;
      
      wikiToDB.insertSentence("sentence1", tmp, true, false, false, 1);
      byte res[];
      
      res = wikiToDB.getFeatures(1);
     /*
      for(int i= 1; i<5; i++) {
        wikiToDB.insertSentenceAndFeatures("file1", "this is a ( )"+ i + " test", "long feature string "+i);
      }
      */
      wikiToDB.closeDBConnection();

      //Get the URLs and send them to wget.
      wikiToDB.createDBConnection("localhost","wiki","marcela","wiki123");
      wikiToDB.setDBTable("dbselection");
      //String sentence = wikiToDB.queryTable(3, "sentence");
      String sentence = wikiToDB.getSentence("dbselection", 3);
      System.out.println("sentence 3 = " + sentence );
      
      String fea = wikiToDB.getFeaturesFromTable(3, "dbselection");
      System.out.println("feature 3 = " + fea );
      
      wikiToDB.closeDBConnection();

  } // end of main()



}
