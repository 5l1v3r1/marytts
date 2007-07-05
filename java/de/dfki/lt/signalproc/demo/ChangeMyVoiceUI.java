/*
 * ChangeMyVoiceUI.java
 *
 * Created on June 21, 2007, 7:52 AM
 */

package de.dfki.lt.signalproc.demo;

import java.awt.Container;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.awt.Point;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.jsresources.AudioCommon;
import org.jsresources.AudioRecorder.BufferingRecorder;

import de.dfki.lt.mary.client.SimpleFileFilter;
import de.dfki.lt.mary.util.MaryUtils;
import de.dfki.lt.signalproc.FFT;
import de.dfki.lt.signalproc.process.FrameOverlapAddSource;
import de.dfki.lt.signalproc.process.InlineDataProcessor;
import de.dfki.lt.signalproc.process.LPCWhisperiser;
import de.dfki.lt.signalproc.process.Robotiser;
import de.dfki.lt.signalproc.process.Chorus;
import de.dfki.lt.signalproc.process.VocalTractScalingProcessor;
import de.dfki.lt.signalproc.process.VocalTractScalingSimpleProcessor;
import de.dfki.lt.signalproc.process.VocalTractModifier;
import de.dfki.lt.signalproc.filter.*;
import de.dfki.lt.signalproc.util.AudioDoubleDataSource;
import de.dfki.lt.signalproc.util.BufferedDoubleDataSource;
import de.dfki.lt.signalproc.util.DDSAudioInputStream;
import de.dfki.lt.signalproc.util.DoubleDataSource;
import de.dfki.lt.signalproc.util.MathUtils;
import de.dfki.lt.signalproc.util.SignalProcUtils;
import de.dfki.lt.signalproc.demo.OnlineAudioEffects;
import de.dfki.lt.signalproc.display.FunctionGraph;
import de.dfki.lt.mary.util.MaryAudioUtils;

/**
 *
 * @author  oytun.turk
 */

public class ChangeMyVoiceUI extends javax.swing.JFrame {
    String playFile;
    File outputFile;
    private int TOTAL_BUILT_IN_TTS_FILES;
    private double amount;
    private int targetIndex;
    private int inputIndex;
    private int recordIndex;
    private boolean bStarted;
    private boolean bRecording;
    private boolean bPlaying;
    OnlineAudioEffects online;
    TargetDataLine microphone;
    SourceDataLine loudspeakers;
    AudioInputStream inputStream;
    BufferingRecorder recorder;
    
    private Vector listItems; //Just the names we see on the list
    private File lastDirectory;
    private File inputFile;
    private String [] inputFileNameList; //Actual full paths to files 
    private String [] builtInFileNameList;
    private String classPath; //Class run-time path
    private String strBuiltInFilePath;
    private String strRecordPath;
    
    VoiceModificationParameters modificationParameters;
    String [] targetNames = { "Robot", 
                              "Whisper", 
                              "Dwarf1",
                              "Dwarf2",
                              "Ogre1",
                              "Ogre2",
                              "Giant1",
                              "Giant2",
                              "Ghost",
                              "Stadium",
                              "Jet Pilot", 
                              "Old Radio", 
                              "Telephone"
                              }; 
    
    /** Creates new form ChangeMyVoiceUI */
    public ChangeMyVoiceUI() {
        playFile = null;
        recorder = null;
        outputFile = null;
        microphone = null;
        loudspeakers = null;
        inputStream = null;
        targetIndex = -1;
        inputIndex = -1;
        inputFile = null;
        bRecording = false;
        bPlaying = false;
        lastDirectory = null;
        inputFileNameList = null;
        listItems = new Vector();
        recordIndex = 0;
        
        classPath = new File(".").getAbsolutePath();
        strBuiltInFilePath = classPath + "\\java\\de\\dfki\\lt\\signalproc\\demo\\demo\\";
        strRecordPath = classPath + "\\java\\de\\dfki\\lt\\signalproc\\demo\\demo\\record\\";
        
        listItems.addElement("Streaming Audio");    
        
        TOTAL_BUILT_IN_TTS_FILES = 4;
        builtInFileNameList = new String[TOTAL_BUILT_IN_TTS_FILES];
        
        listItems.addElement("Built-in TTS Output1 (wohin-bits3.wav)");
        builtInFileNameList[0] = strBuiltInFilePath + "wohin-bits3.wav";
        
        listItems.addElement("Built-in TTS Output2 (herta-neutral.wav)");
        builtInFileNameList[1] = strBuiltInFilePath + "herta-neutral.wav";
        
        listItems.addElement("Built-in TTS Output3 (herta-excited.wav)");
        builtInFileNameList[2] = strBuiltInFilePath + "herta-excited.wav";
        
        listItems.addElement("Built-in TTS Output4 (ausprobieren-bits3.wav)");
        builtInFileNameList[3] = strBuiltInFilePath + "ausprobieren-bits3.wav";
        
        initComponents();
        modificationParameters = new VoiceModificationParameters();
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        jComboBoxTargetVoice = new javax.swing.JComboBox();
        jButtonExit = new javax.swing.JButton();
        jLabelTargetVoice = new javax.swing.JLabel();
        jButtonAdd = new javax.swing.JButton();
        jButtonStart = new javax.swing.JButton();
        jButtonDel = new javax.swing.JButton();
        jButtonPlay = new javax.swing.JButton();
        jLabelLow = new javax.swing.JLabel();
        jScrollList = new javax.swing.JScrollPane();
        jListInput = new javax.swing.JList();
        jLabelChangeAmount = new javax.swing.JLabel();
        jLabelHigh = new javax.swing.JLabel();
        jSliderChangeAmount = new javax.swing.JSlider();
        jLabelInput = new javax.swing.JLabel();
        jButtonRec = new javax.swing.JButton();
        jLabelMedium = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Change My Voice");
        setResizable(false);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        jComboBoxTargetVoice.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxTargetVoiceActionPerformed(evt);
            }
        });

        jButtonExit.setText("Exit");
        jButtonExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonExitActionPerformed(evt);
            }
        });

        jLabelTargetVoice.setText("Target Voice");
        jLabelTargetVoice.setName("");

        jButtonAdd.setText("Add");
        jButtonAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonAddActionPerformed(evt);
            }
        });

        jButtonStart.setText("Start");
        jButtonStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonStartActionPerformed(evt);
            }
        });

        jButtonDel.setText("Del");
        jButtonDel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonDelActionPerformed(evt);
            }
        });

        jButtonPlay.setText("Play");
        jButtonPlay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonPlayActionPerformed(evt);
            }
        });

        jLabelLow.setText("Low");

        jListInput.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jListInput.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                jListInputValueChanged(evt);
            }
        });

        jScrollList.setViewportView(jListInput);

        jLabelChangeAmount.setText("Change Amount");

        jLabelHigh.setText("High");

        jSliderChangeAmount.setMajorTickSpacing(50);
        jSliderChangeAmount.setMinorTickSpacing(5);
        jSliderChangeAmount.setPaintTicks(true);
        jSliderChangeAmount.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                jSliderChangeAmountMouseDragged(evt);
            }
        });
        jSliderChangeAmount.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                jSliderChangeAmountPropertyChange(evt);
            }
        });
        jSliderChangeAmount.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jSliderChangeAmountFocusLost(evt);
            }
        });
        jSliderChangeAmount.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jSliderChangeAmountStateChanged(evt);
            }
        });

        jLabelInput.setText("Input");

        jButtonRec.setText("Rec");
        jButtonRec.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonRecActionPerformed(evt);
            }
        });

        jLabelMedium.setText("Medium");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(10, 10, 10)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jLabelChangeAmount)
                                    .add(jLabelTargetVoice))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                                        .add(jLabelLow)
                                        .add(104, 104, 104)
                                        .add(jLabelMedium)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .add(jLabelHigh))
                                    .add(org.jdesktop.layout.GroupLayout.LEADING, jSliderChangeAmount, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .add(org.jdesktop.layout.GroupLayout.LEADING, jComboBoxTargetVoice, 0, 278, Short.MAX_VALUE)))
                            .add(org.jdesktop.layout.GroupLayout.LEADING, jLabelInput))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 18, Short.MAX_VALUE))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jScrollList, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 379, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jButtonAdd)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, jButtonStart, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(layout.createSequentialGroup()
                                .add(jButtonRec)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jButtonPlay)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButtonDel)
                        .add(80, 80, 80))
                    .add(layout.createSequentialGroup()
                        .add(171, 171, 171)
                        .add(jButtonExit)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(43, 43, 43)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jComboBoxTargetVoice, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabelTargetVoice))
                .add(15, 15, 15)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jSliderChangeAmount, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(8, 8, 8)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabelLow)
                            .add(jLabelHigh)
                            .add(jLabelMedium)))
                    .add(jLabelChangeAmount))
                .add(14, 14, 14)
                .add(jLabelInput)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollList, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 225, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jButtonAdd)
                    .add(jButtonRec)
                    .add(jButtonPlay)
                    .add(jButtonDel))
                .add(23, 23, 23)
                .add(jButtonStart, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 42, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(30, 30, 30)
                .add(jButtonExit)
                .addContainerGap())
        );
        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jSliderChangeAmountFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jSliderChangeAmountFocusLost
       
    }//GEN-LAST:event_jSliderChangeAmountFocusLost

    private void jSliderChangeAmountMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jSliderChangeAmountMouseDragged

    }//GEN-LAST:event_jSliderChangeAmountMouseDragged

    private void jButtonPlayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonPlayActionPerformed
        if (!bRecording && playFile!=null)
        {
            if (!bPlaying)
            {
                bPlaying = true;
                jButtonPlay.setText("Stop");
                
                MaryAudioUtils.playWavFile(playFile, 0, true);
            }
            else
            {
                MaryAudioUtils.stopWavFile();
                
                bPlaying = false;
                jButtonPlay.setText("Play");
            }
            
            jButtonRec.setEnabled(!bPlaying);
            jButtonAdd.setEnabled(!bPlaying);
            jButtonDel.setEnabled(!bPlaying);
            jListInput.setEnabled(!bPlaying);
            jButtonStart.setEnabled(!bPlaying);
        }
    }//GEN-LAST:event_jButtonPlayActionPerformed

    private void jButtonDelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonDelActionPerformed
        if (inputIndex>=TOTAL_BUILT_IN_TTS_FILES+1)
        {
            listItems.remove(inputIndex);
            inputIndex--;
            UpdateInputList();
        }
    }//GEN-LAST:event_jButtonDelActionPerformed

    private void jListInputValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_jListInputValueChanged
        
        getInputIndex();
        
        if (inputIndex==0)
            jButtonPlay.setEnabled(false);
        else
            jButtonPlay.setEnabled(true);
        
        if (inputIndex<this.TOTAL_BUILT_IN_TTS_FILES+1)
            jButtonDel.setEnabled(false);
        else
            jButtonDel.setEnabled(true);
        
        if (inputIndex == 0)
            playFile = null;
        else if (inputIndex>this.TOTAL_BUILT_IN_TTS_FILES)
            playFile = (String)listItems.get(inputIndex);
        else
            playFile = builtInFileNameList[inputIndex-1];
        
    }//GEN-LAST:event_jListInputValueChanged

    //Browse for a new wav file
    private void jButtonAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonAddActionPerformed
        JFileChooser fc = new JFileChooser();
        if (lastDirectory != null) {
            fc.setCurrentDirectory(lastDirectory);
        }

        FileFilter ff = new SimpleFileFilter("wav", "Wav Files (*.wav)");
        fc.addChoosableFileFilter(ff);
        
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) 
        {
            inputFile = fc.getSelectedFile();
            lastDirectory = inputFile.getParentFile();
            listItems.add(inputFile.getPath()); //Keep full path
            UpdateInputList();
            jListInput.setSelectedIndex(listItems.size()-1);
        }
    }//GEN-LAST:event_jButtonAddActionPerformed

    private void jButtonRecActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonRecActionPerformed
        if (!bRecording) //Start recording
        {
            if (recorder != null)
            {
                recorder.stopRecording();
                recorder = null;
            }
            
            int channels = 1;
            
            recordIndex++;
            String strRecordIndex = Integer.toString(recordIndex);
            while (strRecordIndex.length()<5)
                strRecordIndex = "0" + strRecordIndex;
            
            String strFilename = strRecordPath + "NewFile_" + strRecordIndex + ".wav";
            outputFile = new File(strFilename);

            AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                modificationParameters.fs, 16, channels, 2*channels, modificationParameters.fs, false);

            AudioFileFormat.Type    targetType = AudioFileFormat.Type.WAVE;

            if (microphone != null)
                microphone.close();

            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class,
                        audioFormat);

                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(audioFormat, 1024);
                System.out.println("Microphone format: " + microphone.getFormat());

            } catch (LineUnavailableException e) {
                e.printStackTrace();
                System.exit(1);
            }
            
            recorder = new BufferingRecorder(microphone, targetType, outputFile, 0);

            bRecording = true;
            jButtonRec.setText("Stop");
            
            recorder.start();
        }
        else //Stop recording
        {
            recorder.stopRecording();
            recorder = null;
            microphone.close();
            microphone = null;
            
            bRecording = false;
            jButtonRec.setText("Rec");
            
            inputFile = outputFile;
            listItems.add(inputFile.getPath()); //Keep full path
            UpdateInputList();
            jListInput.setSelectedIndex(listItems.size()-1);
        }
        
        jButtonPlay.setEnabled(!bRecording);
        jButtonAdd.setEnabled(!bRecording);
        jButtonDel.setEnabled(!bRecording);
        jListInput.setEnabled(!bRecording);
        jButtonStart.setEnabled(!bRecording);
        
    }//GEN-LAST:event_jButtonRecActionPerformed

    private void jSliderChangeAmountStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jSliderChangeAmountStateChanged
        double prevAmount = amount;

        getAmount();

        if (bStarted && prevAmount-amount>0.001) //If currently processing and changed modification amount
        {
            jButtonStart.doClick(); //Stop
            jButtonStart.doClick(); //and restart to adapt to new target voice
        }
    }//GEN-LAST:event_jSliderChangeAmountStateChanged

    private void jSliderChangeAmountPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_jSliderChangeAmountPropertyChange
// TODO add your handling code here:
    }//GEN-LAST:event_jSliderChangeAmountPropertyChange

    private void jButtonExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonExitActionPerformed
    System.exit(0);
    }//GEN-LAST:event_jButtonExitActionPerformed

    private void jComboBoxTargetVoiceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxTargetVoiceActionPerformed
        int prevTargetIndex = targetIndex;

        getTargetIndex();

        if (bStarted && prevTargetIndex != targetIndex) //If currently processing and changed target voice type
        {
            jButtonStart.doClick(); //Stop
            jButtonStart.doClick(); //and restart to adapt to new target voice
        }
    }//GEN-LAST:event_jComboBoxTargetVoiceActionPerformed
    
    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        
    }//GEN-LAST:event_formMouseClicked

   public void getTargetIndex()
   {
       targetIndex = jComboBoxTargetVoice.getSelectedIndex();
       if (targetNames[targetIndex]=="Telephone")
           modificationParameters.fs = 8000;
       else
           modificationParameters.fs = 16000;
       
       boolean bChangeEnabled = true;
       if (targetNames[targetIndex]=="Jet Pilot" || 
           targetNames[targetIndex]=="Old Radio" ||
           targetNames[targetIndex]=="Telephone")
       {
           bChangeEnabled = false;
       }
       
       jLabelChangeAmount.setEnabled(bChangeEnabled);
       jLabelLow.setEnabled(bChangeEnabled);
       jLabelMedium.setEnabled(bChangeEnabled);
       jLabelHigh.setEnabled(bChangeEnabled);
       jSliderChangeAmount.setEnabled(bChangeEnabled);
   }
    private void jButtonStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonStartActionPerformed
        if (!bRecording)
        {
            if (!bStarted)
            { 
                jButtonStart.setText("Stop");
                jButtonRec.setEnabled(false);
                jButtonPlay.setEnabled(false);
                jButtonAdd.setEnabled(false);
                jButtonDel.setEnabled(false);
                jListInput.setEnabled(false);
                getParameters();
                changeVoice();
            }
            else 
            {
                bStarted = false;
                online.requestStop();

                //Close the source and the target datalines to be able to use them repeatedly
                if (microphone!=null)
                {
                    microphone.close();
                    microphone = null;
                }

                if (loudspeakers != null)
                {
                    loudspeakers.close();
                    loudspeakers = null;
                }

                if (inputStream != null)
                {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    inputStream = null;
                }
                //

                jButtonStart.setText("Start");
                jButtonRec.setEnabled(true);
                jButtonPlay.setEnabled(true);
                jButtonAdd.setEnabled(true);
                if (inputIndex>TOTAL_BUILT_IN_TTS_FILES)
                    jButtonDel.setEnabled(true);
                jListInput.setEnabled(true);
            }
        }   
    }//GEN-LAST:event_jButtonStartActionPerformed

    /* This function gets the modification parameters from the GUI
     * and fills in the modificationParameters object
    */ 
    private void getParameters() {
        getInputIndex();
        getTargetIndex();
        getAmount();
    }
    
    /*This function opens source and target datalines and starts real-time voice modification  
     * using the parameters in the modificationParameters object
     */ 
    private void changeVoice() {
        bStarted = true;
        int channels = 1;

        AudioFormat audioFormat = null;

        if (inputIndex == 0) //Online processing using microphone
        {
            audioFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, modificationParameters.fs, 16, channels, 2*channels, modificationParameters.fs,
                    false);

            if (microphone != null)
                microphone.close();

            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class,
                        audioFormat);

                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(audioFormat, 1024);
                System.out.println("Microphone format: " + microphone.getFormat());

            } catch (LineUnavailableException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        else //Online processing using pre-recorded wav file
        {
            if (inputIndex>0)
            {
                if (inputIndex>this.TOTAL_BUILT_IN_TTS_FILES)
                {
                    String inputFileNameFull = (String)listItems.get(inputIndex);
                    inputFile = new File(inputFileNameFull);
                }
                else
                    inputFile = new File(builtInFileNameList[inputIndex-1]); 
            }
            else
                inputFile = null;
            
            if (inputFile != null)
            {
                try {
                    inputStream = AudioSystem.getAudioInputStream(inputFile);
                } catch (UnsupportedAudioFileException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            if (inputStream != null)
            {
                audioFormat = inputStream.getFormat();
                modificationParameters.fs = (int)audioFormat.getSampleRate();
            }
        }

        if (loudspeakers != null)
            loudspeakers.close();

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class,
                    audioFormat);
            loudspeakers = (SourceDataLine) AudioSystem.getLine(info);
            loudspeakers.open(audioFormat);
            System.out.println("Loudspeaker format: " + loudspeakers.getFormat());
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }

        // Choose an audio effect
        InlineDataProcessor effect = null;

        if (targetNames[targetIndex]=="Robot")
        {  
            effect = new Robotiser.PhaseRemover(4096, 0.7+0.3*amount);
        }
        else if (targetNames[targetIndex]=="Whisper")
        {  
            effect = new LPCWhisperiser(SignalProcUtils.getLPOrder((int)modificationParameters.fs), 0.4+0.6*amount);
        }
        else if (targetNames[targetIndex]=="Dwarf1") //Using freq. domain LP spectrum modification
        {  
            double [] vscales = {1.3+0.5*amount};
            int p = SignalProcUtils.getLPOrder((int)modificationParameters.fs);
            int fftSize = Math.max(SignalProcUtils.getDFTSize((int)modificationParameters.fs), 1024);
            effect = new VocalTractScalingProcessor(p, (int)modificationParameters.fs, fftSize, vscales);
        }
        else if (targetNames[targetIndex]=="Dwarf2") //Using freq. domain DFT magnitude spectrum modification
        {  
            double [] vscales = {1.3+0.5*amount};
            effect = new VocalTractScalingSimpleProcessor(1024, vscales);
        }
        else if (targetNames[targetIndex]=="Ogre1") //Using freq. domain LP spectrum modification
        { 
            double [] vscales = {0.90-0.1*amount};            
            int p = SignalProcUtils.getLPOrder((int)modificationParameters.fs);
            int fftSize = Math.max(SignalProcUtils.getDFTSize((int)modificationParameters.fs), 1024);
            effect = new VocalTractScalingProcessor(p, (int)modificationParameters.fs, fftSize, vscales);
        }
        else if (targetNames[targetIndex]=="Ogre2") //Using freq. domain DFT magnitude spectrum modification
        { 
            double [] vscales = {0.90-0.1*amount};
            effect = new VocalTractScalingSimpleProcessor(1024, vscales);
        }
        else if (targetNames[targetIndex]=="Giant1") //Using freq. domain LP spectrum modification
        {  
            double [] vscales = {0.75-0.1*amount};
            int p = SignalProcUtils.getLPOrder((int)modificationParameters.fs);
            int fftSize = Math.max(SignalProcUtils.getDFTSize((int)modificationParameters.fs), 1024);
            effect = new VocalTractScalingProcessor(p, (int)modificationParameters.fs, fftSize, vscales);
        }
        else if (targetNames[targetIndex]=="Giant2") //Using freq. domain DFT magnitude spectrum modification
        {  
            double [] vscales = {0.75-0.1*amount};
            effect = new VocalTractScalingSimpleProcessor(1024, vscales);
        }
        else if (targetNames[targetIndex]=="Ghost")
        {
            int [] delaysInMiliseconds = {100+(int)(20*amount), 200+(int)(50*amount), 300+(int)(100*amount)};
            double [] amps = {0.8, -0.7, 0.9};
            effect = new Chorus(delaysInMiliseconds, amps, (int)(modificationParameters.fs));
        }
        else if (targetNames[targetIndex]=="Stadium")
        {
            int [] delaysInMiliseconds = {266+(int)(200*amount), 400+(int)(200*amount)};
            double [] amps = {0.54, -0.10};
            effect = new Chorus(delaysInMiliseconds, amps, (int)(modificationParameters.fs));
        }
        else if (targetNames[targetIndex]=="Jet Pilot")
        {  
            double normalizedCutOffFreq1 = 500.0/modificationParameters.fs;
            double normalizedCutOffFreq2 = 2000.0/modificationParameters.fs;
            effect = new BandPassFilter(normalizedCutOffFreq1, normalizedCutOffFreq2, true);
        }
        else if (targetNames[targetIndex]=="Telephone")
        {  
            double normalizedCutOffFreq1 = 300.0/modificationParameters.fs;
            double normalizedCutOffFreq2 = 3400.0/modificationParameters.fs;
            effect = new BandPassFilter(normalizedCutOffFreq1, normalizedCutOffFreq2, true);
        }
        else if (targetNames[targetIndex]=="Old Radio")
        {  
            double normalizedCutOffFreq = 3000.0/modificationParameters.fs;
            effect = new LowPassFilter(normalizedCutOffFreq, true);
        }
        //            

        // Create the output thread and make it run in the background:
        if (effect!=null && loudspeakers!=null)
        {
            if (microphone != null)
                online = new OnlineAudioEffects(effect, microphone, loudspeakers, null);
            else if (inputStream !=null)
                online = new OnlineAudioEffects(effect, inputStream, loudspeakers, jButtonStart);

            online.start();
        }
    }
    
    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        int i;
        
        //Move the window to somewhere closer to middle o screen
        Point p = this.getLocation();
        Dimension d = this.getSize();
        p.x = (int)(0.5*(1500-d.getWidth()));
        p.y = (int)(0.5*(1000-d.getHeight()));
        this.setLocation(p);
        //
        
        bStarted = false;
        
        //Fill-in target voice combo-box
        for (i=0; i<targetNames.length; i++) {
            jComboBoxTargetVoice.addItem(targetNames[i]);
        }
        //
        
        //Fill-in input combo-box
        inputIndex = 0;
        UpdateInputList();
        //
        
        getParameters();
        
    }//GEN-LAST:event_formWindowOpened
    
    public void UpdateInputList()
    {
        File fTmp;
        int i;
        inputFileNameList = new String[listItems.size()];
        
        for (i=0; i<listItems.size(); i++)
        {
            fTmp = new File((String)listItems.get(i));
            inputFileNameList[i] = fTmp.getName();
        }
        
        inputIndex = Math.min(listItems.size()-1, inputIndex);
        inputIndex = Math.max(0, inputIndex);
        
        int prevInputIndex = inputIndex;
        
        jListInput.setListData(inputFileNameList);
        
        inputIndex = prevInputIndex;
        
        jListInput.setSelectedIndex(inputIndex);
    }
    
    public void getAmount()
    { 
        amount = (((double)jSliderChangeAmount.getValue())-jSliderChangeAmount.getMinimum())/(((double)jSliderChangeAmount.getMaximum())-jSliderChangeAmount.getMinimum());
        amount = Math.min(amount, 1.0);
        amount = Math.max(amount, 0.0); 
    }
    
    public void getInputIndex()
    {
        inputIndex = jListInput.getSelectedIndex();
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ChangeMyVoiceUI().setVisible(true);
            }
        });
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonAdd;
    private javax.swing.JButton jButtonDel;
    private javax.swing.JButton jButtonExit;
    private javax.swing.JButton jButtonPlay;
    private javax.swing.JButton jButtonRec;
    private javax.swing.JButton jButtonStart;
    private javax.swing.JComboBox jComboBoxTargetVoice;
    private javax.swing.JLabel jLabelChangeAmount;
    private javax.swing.JLabel jLabelHigh;
    private javax.swing.JLabel jLabelInput;
    private javax.swing.JLabel jLabelLow;
    private javax.swing.JLabel jLabelMedium;
    private javax.swing.JLabel jLabelTargetVoice;
    private javax.swing.JList jListInput;
    private javax.swing.JScrollPane jScrollList;
    private javax.swing.JSlider jSliderChangeAmount;
    // End of variables declaration//GEN-END:variables
}
