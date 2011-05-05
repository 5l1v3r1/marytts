/**
 * Copyright 2011 DFKI GmbH.
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
package marytts.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.StringTokenizer;

import marytts.exceptions.MaryConfigurationException;

/**
 * @author marc
 *
 */
public abstract class MaryConfig {
	private static final ServiceLoader<MaryConfig> configLoader = ServiceLoader.load(MaryConfig.class);
	
	/**
	 * This method will try to check that the available configs are consistent and will spot obvious reasons why
	 * they might not work together as a full system. Reasons that are detected include:
	 * <ul>
	 *   <li>There is no main config;</li>
	 *   <li>There is a voice with a certain locale but no language component has that locale.</li>
	 * </ul>
	 * This method will return allright if everything is OK; if there is a problem, it will throw an Exception
	 * with a message indicating the problem.
	 * @throws MaryConfigurationException if the configuration cannot work as it is now.
	 */
	public static void checkConsistency() throws MaryConfigurationException {
		// Check that we have a main config
		if (getMainConfig() == null) {
			throw new MaryConfigurationException("No main config");
		}
		// Check that for each voice, we have a matching language config
		for (VoiceConfig vc : getVoiceConfigs()) {
			if (getLanguageConfig(vc.getLocale()) == null) {
				throw new MaryConfigurationException("Voice '"+vc.getName()+"' has locale '"+vc.getLocale()+"', but there is no corresponding language config.");
			}
		}
	}
	
	public static int countConfigs() {
		int num = 0;
		for (@SuppressWarnings("unused") MaryConfig mc : configLoader) {
			num++;
		}
		return num;
	}
		
	public static int countLanguageConfigs() {
		int num = 0;
		for (MaryConfig mc : configLoader) {
			if (mc.isLanguageConfig()) {
				num++;
			}
		}
		return num;
	}

	public static int countVoiceConfigs() {
		int num = 0;
		for (MaryConfig mc : configLoader) {
			if (mc.isVoiceConfig()) {
				num++;
			}
		}
		return num;
	}

	public static MaryConfig getMainConfig() {
		for (MaryConfig mc : configLoader) {
			if (mc.isMainConfig()) {
				return mc;
			}
		}
		return null;
	}
	
	public static Iterable<LanguageConfig> getLanguageConfigs() {
		Set<LanguageConfig> lcs = new HashSet<LanguageConfig>();
		for (MaryConfig mc : configLoader) {
			if (mc.isLanguageConfig()) {
				LanguageConfig lc = (LanguageConfig) mc;
				lcs.add(lc);
			}
		}
		return lcs;
	}
	
	public static Iterable<VoiceConfig> getVoiceConfigs() {
		Set<VoiceConfig> vcs = new HashSet<VoiceConfig>();
		for (MaryConfig mc : configLoader) {
			if (mc.isVoiceConfig()) {
				VoiceConfig lc = (VoiceConfig) mc;
				vcs.add(lc);
			}
		}
		return vcs;
	}
	
	public static LanguageConfig getLanguageConfig(Locale locale) {
		for (MaryConfig mc : configLoader) {
			if (mc.isLanguageConfig()) {
				LanguageConfig lc = (LanguageConfig) mc;
				if (lc.getLocales().contains(locale)) {
					return lc;
				}
			}
		}
		return null;
	}
	
	public static VoiceConfig getVoiceConfig(String voiceName) {
		for (MaryConfig mc : configLoader) {
			if (mc.isVoiceConfig()) {
				VoiceConfig vc = (VoiceConfig) mc;
				if (vc.getName().equals(voiceName)) {
					return vc;
				}
			}
		}
		return null;
	}
	
	public static Iterable<MaryConfig> getConfigs() {
		return configLoader;
	}
	
	
	
	//////////// Non-static / base class methods //////////////
	
	private Properties props;
	
	protected MaryConfig(InputStream propertyStream) throws MaryConfigurationException {
		props = new Properties();
		try {
			props.load(propertyStream);
		} catch (Exception e) {
			throw new MaryConfigurationException("cannot load properties", e);
		}
	}
	
	public boolean isMainConfig() {
		return false;
	}
	
	public boolean isLanguageConfig() {
		return false;
	}
	
	public boolean isVoiceConfig() {
		return false;
	}
	
	public Properties getProperties() {
		return props;
	}
	
	/**
	 * For the given property name, return the value of that property as a list of items (interpreting the property value as a space-separated list).
	 * @param propertyName
	 * @return the list of items, or an empty list if the property is not defined or contains no items
	 */
	public List<String> getList(String propertyName) {
		String val = props.getProperty(propertyName);
		List<String> items = new ArrayList<String>();
		if (val != null) {
			for (StringTokenizer st = new StringTokenizer(val); st.hasMoreTokens(); ) {
				items.add(st.nextToken());
			}
		}
		return items;
	}
	
}
