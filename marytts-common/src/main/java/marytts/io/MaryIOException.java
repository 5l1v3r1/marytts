package marytts.io;

/**
 *
 *
 * @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le Maguer</a>
 */
public class MaryIOException extends MaryException
{
    public MaryIOException(String message, Exception ex)
    {
        super(message, ex);
    }
}
