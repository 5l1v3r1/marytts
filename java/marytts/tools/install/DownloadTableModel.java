/**
 * Copyright 2000-2009 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.tools.install;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JProgressBar;
import javax.swing.table.AbstractTableModel;

import marytts.tools.install.InstallableVoice.Status;


// This class manages the download table's data.
class DownloadsTableModel extends AbstractTableModel
        implements Observer {
    
    // These are the names for the table's columns.
    private static final String[] columnNames = {"Voice", "Version", "Size",
    "Progress", "Status", "License"};
    
    // These are the classes for each column's values.
    private static final Class[] columnClasses = {String.class, String.class,
    String.class, JProgressBar.class, String.class, String.class};
    
    // The table's list of downloads.
    private ArrayList<InstallableVoice> downloadList = new ArrayList<InstallableVoice>();
    
    // Add a new download to the table.
    public void addDownload(InstallableVoice download) {
        
        // Register to be notified when the download changes.
        download.addObserver(this);
        
        downloadList.add(download);
        
        // Fire table row insertion notification to table.
        fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
    }
    
    // Get a download for the specified row.
    public InstallableVoice getDownload(int row) {
        if (row < 0  || row >= downloadList.size()) return null;
        return downloadList.get(row);
    }
    
    // Remove a download from the list.
    public void clearDownload(int row) {
        downloadList.remove(row);
        
        // Fire table row deletion notification to table.
        fireTableRowsDeleted(row, row);
    }

    
    // Get table's column count.
    public int getColumnCount() {
        return columnNames.length;
    }
    
    // Get a column's name.
    public String getColumnName(int col) {
        return columnNames[col];
    }
    
    // Get a column's class.
    public Class getColumnClass(int col) {
        return columnClasses[col];
    }
    
    // Get table's row count.
    public int getRowCount() {
        return downloadList.size();
    }
    
    // Get value for a specific row and column combination.
    public Object getValueAt(int row, int col) {
        
        InstallableVoice download = downloadList.get(row);
        switch (col) {
            case 0: // name
                return download.getName();
            case 1: // version
                return download.getVersion();
            case 2: // Size
                int size = download.getSize();
                return (size == -1) ? "" : Integer.toString(size);
            case 3: // Progress
                Status status = download.getStatus();
                if (status == Status.DOWNLOADING)
                    return new Float(download.getProgress());
                return null;
            case 4: // Status
                return download.getStatus();
            case 5: // License
                String license = download.getLicenseUrl();
                if (license == null) return null;
                int lastSlash = license.lastIndexOf('/');
                if (lastSlash == -1) return license;
                int underscoreAfterSlash = license.indexOf('_', lastSlash);
                if (underscoreAfterSlash == -1) return license.substring(lastSlash+1);
                return license.substring(lastSlash+1, underscoreAfterSlash);
        }
        return "";
    }
    
  /* Update is called when a Download notifies its
     observers of any changes */
    public void update(Observable o, Object arg) {
        int index = downloadList.indexOf(o);
        
        // Fire table row update notification to table.
        fireTableRowsUpdated(index, index);
    }
}

