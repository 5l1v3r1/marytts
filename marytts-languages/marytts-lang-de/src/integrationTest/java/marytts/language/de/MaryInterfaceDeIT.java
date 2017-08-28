package marytts.language.de;

import java.util.Locale;

import marytts.LocalMaryInterface;

import marytts.exceptions.SynthesisException;
import org.testng.Assert;
import org.testng.annotations.*;

public class MaryInterfaceDeIT {

    @Test
    public void canSetLocale() throws Exception {
        LocalMaryInterface mary = new LocalMaryInterface();
        Locale loc = Locale.GERMAN;
        Assert.assertTrue(!loc.equals(mary.getLocale()));
        mary.setLocale(loc);
        Assert.assertEquals(loc, mary.getLocale());
    }
}
