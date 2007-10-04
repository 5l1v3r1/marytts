package de.dfki.lt.signalproc.effects;

public class JetPilotEffect extends FilterEffectsBase {
    
    public JetPilotEffect(int samplingRate)
    {
        super(500.0, 2000.0, samplingRate, BANDPASS_FILTER);
    }
    

    public void parseParameters(String param)
    {
        initialise();
    }
    
    public String getHelpText() {
        String strHelp = "Jet pilot effect:\n\n" +
                         "Filters the input signal using an FIR bandpass filter.\n\n" +
                         "Parameters: NONE\n";
                        
        return strHelp;
    }

    public String getName() {
        return "JetPilot";
    }
}
