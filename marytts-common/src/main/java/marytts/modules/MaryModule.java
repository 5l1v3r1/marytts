/**
 * Copyright 2000-2006 DFKI GmbH.
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
package marytts.modules;

import marytts.config.MaryConfiguration;
import marytts.config.MaryConfigurationFactory;
import marytts.exceptions.MaryConfigurationException;

import java.io.StringReader;
import java.util.Locale;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import marytts.data.Utterance;
import marytts.util.MaryUtils;
import marytts.MaryException;

// Logging
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;

/**
 *
 */

public abstract class MaryModule {
    public static final int MODULE_OFFLINE = 0;
    public static final int MODULE_RUNNING = 1;

    private MaryConfiguration default_configuration = null;
    protected int state;

    /**
     * The logger instance to be used by this module. It will identify the
     * origin of the log message in the log file.
     */
    protected Logger logger;


    protected MaryModule() {
	this(MaryConfigurationFactory.getDefaultConfiguration());
    }

    protected MaryModule(MaryConfiguration default_configuration) {
	this.default_configuration = default_configuration;
        logger = LogManager.getLogger(this);
        this.state = MODULE_OFFLINE;
    }

    public int getState() {
        return state;
    }

    public MaryConfiguration getDefaultConfiguration() {
	return default_configuration;
    }

    public void applyDefaultConfiguration() throws MaryConfigurationException {
	if (default_configuration != null) {
	    default_configuration.applyConfiguration(this);
	}
    }

    public void startup() throws Exception {
        assert state == MODULE_OFFLINE;
	applyDefaultConfiguration();
        state = MODULE_RUNNING;

	logger.debug("\n" + MaryConfigurationFactory.dump());

        logger.info("Module " + this.getClass().toGenericString() + " started.");
    }

    public abstract void checkStartup() throws MaryConfigurationException;

    public void shutdown() {
        logger.info("Module shut down.");
        state = MODULE_OFFLINE;
    }

    protected void addAppender(Appender app) {
	((org.apache.logging.log4j.core.Logger) this.logger).addAppender(app);
    }

    /**
     *  Check if the input contains all the information needed to be
     *  processed by the module.
     *
     *  @param utt the input utterance
     *  @throws MaryException which indicates what is missing if something is missing
     */
    public abstract void checkInput(Utterance utt) throws MaryException;

    public Utterance process(Utterance utt) throws Exception {
        return process(utt, new MaryConfiguration());
    }

    public Utterance process(Utterance utt, Appender app) throws Exception {
	((org.apache.logging.log4j.core.Logger) this.logger).addAppender(app);
        return process(utt, new MaryConfiguration());
    }


    public Utterance process(Utterance utt, MaryConfiguration runtime_configuration, Appender app) throws Exception {
	addAppender(app);
        return process(utt, runtime_configuration);
    }

    public abstract Utterance process(Utterance utt, MaryConfiguration runtime_configuration) throws Exception;
}
