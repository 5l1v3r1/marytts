package de.dfki.lt.mary.installvoices;

import java.awt.Dimension;
import java.awt.Frame;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

// This class downloads a file from a URL.
public class InstallableVoice extends Observable implements Runnable {
    public enum Status {AVAILABLE, DOWNLOADING, PAUSED, DOWNLOADED, CANCELLED, ERROR, INSTALLED};
    
    // Max size of download buffer.
    private static final int MAX_BUFFER_SIZE = 1024;
    
    private String name;
    private String version;
    private String archiveFilename;
    private String infoFilename;
    private URL url; // download URL
    private URL license;
    private int size; // size of download in bytes
    private int downloaded; // number of bytes downloaded
    private Status status; // current status of download
    
    // Constructor for Download.
    public InstallableVoice(String name, String version, String archiveFilename, String infoFilename, URL url, int size, URL license) {
        this.name = name;
        this.version = version;
        this.archiveFilename = archiveFilename;
        this.infoFilename = infoFilename;
        this.url = url;
        this.size = size;
        this.license = license;
        downloaded = 0;
        determineStatus();
    }
    
    public String getName()
    {
        return name;
    }
    
    public String getVersion()
    {
        return version;
    }
    
    public String getArchiveFilename()
    {
        return archiveFilename;
    }
    
    public String getInfoFilename()
    {
        return infoFilename;
    }
    
    // Get this download's URL.
    public String getUrl() {
        return url.toString();
    }
    
    // Get this download's size.
    public int getSize() {
        return size;
    }
    
    // Get this download's progress.
    public float getProgress() {
        return ((float) downloaded / size) * 100;
    }
    
    // Get this download's status.
    public Status getStatus() {
        return status;
    }
    
    private void determineStatus()
    {
        if (infoFilename != null && new File(infoFilename).exists()) status = Status.INSTALLED;
        else if (archiveFilename != null && new File(archiveFilename).exists()) status = Status.DOWNLOADED;
        else status = Status.AVAILABLE;
    }
    
    // Pause this download.
    public void pause() {
        status = Status.PAUSED;
        stateChanged();
    }
    
    // Resume this download.
    public void resume() {
        status = Status.DOWNLOADING;
        stateChanged();
        download();
    }
    
    // Cancel this download.
    public void cancel() {
        status = Status.CANCELLED;
        stateChanged();
    }
    
    // Mark this download as having an error.
    private void error() {
        status = Status.ERROR;
        stateChanged();
    }
    
    // Start or resume downloading.
    public void download() {
        Thread thread = new Thread(this);
        thread.start();
    }

   
    // Download file.
    public void run() {
        status = Status.DOWNLOADING;
        
        RandomAccessFile file = null;
        InputStream stream = null;
        
        try {
            // Open connection to URL.
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            
            // Specify what portion of file to download.
            connection.setRequestProperty("Range",
                    "bytes=" + downloaded + "-");
            
            // Connect to server.
            connection.connect();
            
            // Make sure response code is in the 200 range.
            if (connection.getResponseCode() / 100 != 2) {
                error();
            }
            
            // Check for valid content length.
            int contentLength = connection.getContentLength();
            if (contentLength < 1) {
                error();
            }
            
      /* Set the size for this download if it
         hasn't been already set. */
            if (size == -1) {
                size = contentLength;
                stateChanged();
            }
            
            // Open file and seek to the end of it.
            file = new RandomAccessFile(archiveFilename, "rw");
            file.seek(downloaded);
            
            stream = connection.getInputStream();
            while (status == Status.DOWNLOADING) {
        /* Size buffer according to how much of the
           file is left to download. */
                byte buffer[];
                if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    buffer = new byte[size - downloaded];
                }
                
                // Read from server into buffer.
                int read = stream.read(buffer);
                if (read == -1)
                    break;
                
                // Write buffer to file.
                file.write(buffer, 0, read);
                downloaded += read;
                stateChanged();
            }
            
      /* Change status to complete if this point was
         reached because downloading has finished. */
            if (status == Status.DOWNLOADING) {
                status = Status.DOWNLOADED;
                stateChanged();
            }
        } catch (Exception e) {
            error();
        } finally {
            // Close file.
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {}
            }
            
            // Close connection to server.
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {}
            }
        }
    }
    
    // Notify observers that this download's status has changed.
    private void stateChanged() {
        setChanged();
        notifyObservers();
    }
    
    public String toString() 
    {
        if (!"".equals(version))
            return name+"-"+version;
        return name;
    }
    
    /**
     * Uninstall this voice.
     * @return true if voice was successfully uninstalled, false otherwise.
     */
    public boolean uninstall()
    {
        int answer = JOptionPane.showConfirmDialog(null, "Completely remove voice '"+toString()+"' from the file system?", "Confirm voice uninstall", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return false;
        }
        try {
            String maryBase = System.getProperty("mary.base");
            System.out.println("Removing "+name+"-"+version+" from "+maryBase+"...");
            BufferedReader br = new BufferedReader(new FileReader(infoFilename));
            LinkedList<String> files = new LinkedList<String>();
            String line = null;
            while ((line = br.readLine()) != null) {
                files.addFirst(line); // i.e., reverse order
            }
            for (String file: files) {
                if (file.trim().equals("")) continue; // skip empty lines
                File f = new File(maryBase+"/"+file);
                if (f.isDirectory()) {
                    String[] kids = f.list();
                    if (kids.length == 0) {
                        System.err.println("Removing empty directory: "+file);
                        f.delete();
                    } else {
                        System.err.println("Cannot delete non-empty directory: "+file);
                    }
                } else if (f.exists()){ // not a directory
                    System.err.println("Removing file: "+file);
                    f.delete();
                } else { // else, file doesn't exist
                    System.err.println("File doesn't exist -- cannot delete: "+file);
                }
            }
            new File(infoFilename).delete();
        } catch (Exception e) {
            System.err.println("Cannot uninstall:");
            e.printStackTrace();
            return false;
        }
        determineStatus();
        return true;
    }
    
    /**
     * Install this voice, if the user accepts the license.
     * @return true if the voice was installed, false otherwise
     */
    public boolean install() throws Exception
    {
        JTextPane licensePane = new JTextPane();
        licensePane.setPage(license);
        JScrollPane scroll = new JScrollPane(licensePane);
        final JOptionPane optionPane = new JOptionPane(scroll, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null, new String[] {"Reject", "Accept"}, "Reject");
        optionPane.setPreferredSize(new Dimension(640,480));
        final JDialog dialog = new JDialog((Frame)null, "Do you accept the following license?", true);
        dialog.setContentPane(optionPane);
        optionPane.addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent e) {
                        String prop = e.getPropertyName();

                        if (dialog.isVisible() 
                         && (e.getSource() == optionPane)
                         && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
                            dialog.setVisible(false);
                        }
                    }
                });        dialog.pack();
        dialog.setVisible(true);
        
        if (!"Accept".equals(optionPane.getValue())) {
            System.out.println("License not accepted. Installation aborted.");
            return false;
        }
        System.out.println("License accepted.");
        String maryBase = System.getProperty("mary.base");
        System.out.println("Installing "+name+"-"+version+" in "+maryBase+"...");
        StringBuffer files = new StringBuffer();
        try {
            ZipFile zipfile = new ZipFile(archiveFilename);
            Enumeration entries = zipfile.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry)entries.nextElement();
                files.append(entry.getName());
                files.append("\n");
                if(entry.isDirectory()) {
                  System.err.println("Extracting directory: " + entry.getName());
                  (new File(maryBase+"/"+entry.getName())).mkdir();
                } else {
                    System.err.println("Extracting file: " + entry.getName());
                    copyInputStream(zipfile.getInputStream(entry),
                       new BufferedOutputStream(new FileOutputStream(maryBase+"/"+entry.getName())));
                }
              }
              zipfile.close();
              PrintWriter pw = new PrintWriter(infoFilename);
              pw.println(files);
              pw.close();
        } catch (Exception e) {
            System.err.println("... installation failed:");
            e.printStackTrace();
            return false;
        }
        System.err.println("...done");
        status = Status.INSTALLED;
        return true;
    }
    
    public static final void copyInputStream(InputStream in, OutputStream out)
    throws IOException
    {
      byte[] buffer = new byte[1024];
      int len;

      while((len = in.read(buffer)) >= 0)
        out.write(buffer, 0, len);

      in.close();
      out.close();
    }
}