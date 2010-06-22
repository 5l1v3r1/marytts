package marytts.tools.voiceimport;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import marytts.features.FeatureDefinition;
import marytts.features.FeatureVector;
import marytts.modules.phonemiser.AllophoneSet;
import marytts.unitselection.data.FeatureFileReader;
import marytts.unitselection.data.Unit;
import marytts.unitselection.data.UnitFileReader;
import marytts.util.math.MathUtils;
import marytts.util.math.PCA;
import marytts.util.math.Regression;
import marytts.util.MaryUtils;

/***
 * Modelling duration using Sum of products (SoP) 
 * @author marcela
 *
 */
public class DurationSoPTrainer extends VoiceImportComponent
{
  protected String features;
  protected DatabaseLayout db = null;
  protected int percent = 0;
  protected boolean success = true;
  protected boolean intercepTerm = true;
  protected File unitlabelDir;
  protected File unitfeatureDir;
  
  private final String name = "DurationSoPTrainer";
  private final String LABELDIR = name+".labelDir";
  private final String FEATUREDIR = name+".featureDir";   
  private final String FEATUREFILE = name+".featureFile";
  private final String UNITFILE = name+".unitFile";
  private final String ALLOPHONESFILE = name+".allophonesFile";
  
  
  public String getName(){
    return name;
}

  public void initialiseComp()
  {
    this.unitlabelDir = new File(getProp(LABELDIR));
    this.unitfeatureDir = new File(getProp(FEATUREDIR));
    String rootDir = db.getProp(db.ROOTDIR);
  }

  public SortedMap<String, String> getDefaultProps(DatabaseLayout dbl){
    this.db = dbl;
    if (props == null){
      props = new TreeMap<String, String>();
      String fileSeparator = System.getProperty("file.separator");
      props.put(FEATUREDIR, db.getProp(db.ROOTDIR) + "phonefeatures" + fileSeparator);
      props.put(LABELDIR, db.getProp(db.ROOTDIR) + "phonelab" + fileSeparator);            
      props.put(FEATUREFILE, db.getProp(db.FILEDIR) + "phoneFeatures"+db.getProp(db.MARYEXT));
      props.put(UNITFILE, db.getProp(db.FILEDIR) + "phoneUnits"+db.getProp(db.MARYEXT));
      props.put(ALLOPHONESFILE, db.getProp(db.ALLOPHONESET));
    }
   return props; 
  }
  
  protected void setupHelp(){
    props2Help = new TreeMap<String, String>();
    props2Help.put(FEATUREDIR, "directory containing the phonefeatures");
    props2Help.put(LABELDIR, "directory containing the phone labels");                
    props2Help.put(FEATUREFILE, "file containing all phone units and their target cost features");
    props2Help.put(UNITFILE, "file containing all phone units");
    props2Help.put(ALLOPHONESFILE, "allophones set file (XML format) it will be taken from ../openmary/lib/modules/language/...)");
  }
  
  protected void setSuccess(boolean val)
  {
      success = val;
  }  
  
  public boolean compute() throws Exception
  {
    String durDir = db.getProp(db.TEMPDIR);
    String vowelsFile = durDir+"vowels.feats";
    String consonantsFile = durDir+"consonants.feats";
    String[] lingFactorsVowel;
    String[] lingFactorsConsonant; 
      
    AllophoneSet allophoneSet;   
    String phoneXML = getProp(ALLOPHONESFILE);
    System.out.println("Reading allophones set from file: " + phoneXML);
    allophoneSet = AllophoneSet.getAllophoneSet(phoneXML);          
    
    FeatureFileReader featureFile = FeatureFileReader.getFeatureFileReader(getProp(FEATUREFILE));
    UnitFileReader unitFile = new UnitFileReader(getProp(UNITFILE));

    FeatureDefinition featureDefinition = featureFile.getFeatureDefinition();
    FeatureVector fv;
    int nUnitsVowel = 0;
    int nUnitsConsonant = 0;
    
    //System.out.println("Feature names: " + featureDefinition.getFeatureNames());
    // select features that will be used as linguistic factors on the regression
    lingFactorsVowel = selectLinguisticFactors(featureDefinition.getFeatureNames(), "Select linguistic factors for vowels:");
    lingFactorsConsonant = selectLinguisticFactors(featureDefinition.getFeatureNames(), "Select linguistic factors for consonants:");
    
    PrintWriter toVowelsFile = new PrintWriter(new FileOutputStream(vowelsFile));
    PrintWriter toConsonantsFile = new PrintWriter(new FileOutputStream(consonantsFile));
    /*
    Vector<Double> vowelDur = new Vector<Double>();
    Vector<Double> vowel = new Vector<Double>();
    Vector<Double> consonantDur = new Vector<Double>();
    Vector<Double> consonant = new Vector<Double>();
    */
    
    int k = 0;
    //int numData = 0;
    int numVowels=0;
    int numConsonants=0;
    // index of phone
    int phoneIndex = featureDefinition.getFeatureIndex("phone");
    for (int i=0, len=unitFile.getNumberOfUnits(); i<len; i++) {
      // We estimate that feature extraction takes 1/10 of the total time
      // (that's probably wrong, but never mind)
      percent = 10*i/len;
      
      Unit u = unitFile.getUnit(i);
      double dur = u.duration / (float) unitFile.getSampleRate();              
      
      fv = featureFile.getFeatureVector(i); 
      
      // first select vowell phones
      // when phone is 0 ??
      if(fv.getByteFeature(phoneIndex) > 0 && dur >= 0.01 ){  
        if (allophoneSet.getAllophone(fv.getFeatureAsString(phoneIndex, featureDefinition)).isVowel()){
          for(int j=0; j < lingFactorsVowel.length; j++)
            toVowelsFile.print(fv.getByteFeature(featureDefinition.getFeatureIndex(lingFactorsVowel[j])) + " ");
          //toVowelsFile.println(Math.log(dur)); // last column is the dependent variable, in this case duration
          toVowelsFile.println(dur);
          numVowels++;
        } else {
          for(int j=0; j < lingFactorsConsonant.length; j++)
            toConsonantsFile.print(fv.getByteFeature(featureDefinition.getFeatureIndex(lingFactorsConsonant[j])) + " ");
          //toConsonantsFile.println(Mat.log(dur)); 
          toConsonantsFile.println(dur);
          numConsonants++;
        }
      }          
   }
   toVowelsFile.close();
   toConsonantsFile.close();
   percent = 10; 
   int cols, rows;
   
   
   // -----------------------------------------------------------------------------------------
   // VOWELS results:
   cols = lingFactorsVowel.length;
   rows = numVowels;
   System.out.println("\nProcessing Vowels:");
   System.out.println("Number of duration points: " + rows);
   System.out.println("Number of linguistic factors: " + cols);   
   
   /*
   System.out.println("PCA analysis:");   
   PCA pcaVowel = new PCA();   
   pcaVowel.principalComponentAnalysis(vowelsFile, false, true);
   pcaVowel.printPricipalComponents(lingFactorsVowel, 1);   
   pcaVowel.printImportanceOfComponents();
   */
   
   System.out.println("Linear regression analysis:");
   Regression regVowel = new Regression(); 
   //regVowel.multipleLinearRegression(vowelsFile, true);
   int c[] = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};  // the last column is always the independent variable
   regVowel.multipleLinearRegression(vowelsFile, cols, c, lingFactorsVowel, true);
   
   regVowel.printCoefficients(c, lingFactorsVowel);
   System.out.println("Correlation vowels original duration / predicted duration = " + regVowel.getCorrelation());
  
   //-----------------------------------------------------------------------------------------
   //CONSONANTS results:
/*   
   cols = lingFactorsConsonant.size();  // linguistic factors plus duration
   rows = numConsonants;
   System.out.println("\nResults for Consonants:");
   System.out.println("Number of duration points: " + rows);
   System.out.println("Number of linguistic factors: " + cols);   
   
   PCA pcaConsonant = new PCA();   
   pcaConsonant.principalComponentAnalysis(consonantsFile, false, true);
   pcaConsonant.printPricipalComponents(lingFactorsConsonant, 1); 
   pcaConsonant.printImportanceOfComponents();
   
   Regression regConsonant = new Regression(); 
   regConsonant.multipleLinearRegression(consonantsFile, true);
   regConsonant.printCoefficients(lingFactorsConsonant);
   System.out.println("Correlation vowels original duration / predicted duration = " + regConsonant.getCorrelation());

*/ 
    percent = 100;
    return true;
  }
  
  public String[] selectLinguisticFactors(String featureNames, String label) throws IOException
  {
      String[] lingFactors=null;
      features = checkFeatureList(featureNames);
 
      final JFrame frame = new JFrame(label);
      GridBagLayout gridBagLayout = new GridBagLayout();
      GridBagConstraints gridC = new GridBagConstraints();
      frame.getContentPane().setLayout( gridBagLayout );
      
      final JEditorPane editPane = new JEditorPane();
      editPane.setPreferredSize(new Dimension(500, 500));
      editPane.setText(features);        
      
      JButton saveButton = new JButton("Save");
      saveButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {                
             setSuccess(true);
             frame.setVisible(false);
          }
      });
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
              setSuccess(false);
              frame.setVisible(false);
          }
      });
      
      gridC.gridx = 0;
      gridC.gridy = 0;
      // resize scroll pane:
      gridC.weightx = 1;
      gridC.weighty = 1;
      gridC.fill = GridBagConstraints.HORIZONTAL;
      JScrollPane scrollPane = new JScrollPane(editPane);
      scrollPane.setPreferredSize(editPane.getPreferredSize());
      gridBagLayout.setConstraints( scrollPane, gridC );
      frame.getContentPane().add(scrollPane);
      gridC.gridy = 1;
      // do not resize buttons:
      gridC.weightx = 0;
      gridC.weighty = 0;
      JPanel buttonPanel = new JPanel();
      buttonPanel.setLayout(new FlowLayout());
      buttonPanel.add(saveButton);
      buttonPanel.add(cancelButton);
      gridBagLayout.setConstraints( buttonPanel, gridC );
      frame.getContentPane().add(buttonPanel);
      frame.pack();
      frame.setVisible(true);
      
      do {
          try {
              Thread.sleep(10); 
          } catch (InterruptedException e) {}
      } while (frame.isVisible());
      frame.dispose();

      if (success) {
          try{
              lingFactors = saveFeatures(editPane.getText());                
          } catch (Exception ex){
              ex.printStackTrace();
              throw new Error("Error defining replacements");
          }
      }
     //return true;
      return lingFactors;
  }

  private String checkFeatureList(String featureNames) throws IOException
  {
      String featureList = "";
      String recommendedFeatureList = "";
      String feaList[] = featureNames.split(" ");
      String line;
     
      for(int i=0; i<feaList.length; i++){
          line = feaList[i];
          
          // CHECK: Maybe we need to exclude some features from the selection list???
          // The following have variance 0
          if( !(line.contains("style") ||
                line.contains("sentence_punc") ||
                line.contains("next_punctuation") ||
                line.contains("prev_punctuation") ||
                line.contains("ph_cplace") ||
                line.contains("ph_cvox") ||
                line.contains("ph_vc") ||
                line.contains("onsetcoda") ||
                line.contains("edge") )) {
            
               // CHECK: here i am including arbitrarily some....
              // put in front the recomended ones: "ph_vfront","ph_vrnd","position_type","pos_in_syl"                
              if( line.contentEquals("ph_vfront") ||
                  line.contentEquals("ph_height") ||
                  line.contentEquals("ph_vlng") ||
                  line.contentEquals("ph_vrnd") ||
                  line.contentEquals("ph_cplace") ||
                  line.contentEquals("ph_ctype") ||
                  line.contentEquals("ph_cvox") ||
                  line.contentEquals("phone") ||
                  line.contentEquals("position_type") )
                    recommendedFeatureList += line + "\n";
              else
                 featureList += line + "\n";
          }
              
        }
      return recommendedFeatureList + "\n" + featureList;
      //return "";

  }
 
  
  private String[] saveFeatures(String newFeatures)
  {     
     String fea[] = newFeatures.split("\n");
     String[] lingFactors = new String[fea.length];
     System.out.print("Selected linguistic factors (" + fea.length + "):");
     for(int i=0; i<fea.length; i++){
       System.out.print(fea[i] + " ");
       lingFactors[i] = fea[i];
     }     
     System.out.println();
     return lingFactors;
  }  
  
  
  public int getProgress()
  {
      return percent;
  }


  
  public static void main(String[] args) throws Exception
  {
      DurationSoPTrainer sop = new DurationSoPTrainer(); 
      DatabaseLayout db = new DatabaseLayout(sop);
      sop.compute();
      
  }

  
  
}
