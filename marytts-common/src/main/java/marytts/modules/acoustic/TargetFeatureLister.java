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
package marytts.modules.acoustic;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import marytts.data.SupportedSequenceType;
import marytts.data.Relation;
import marytts.data.utils.IntegerPair;
import marytts.datatypes.MaryData;
import marytts.datatypes.MaryXML;
import marytts.util.dom.MaryDomUtils;

import marytts.modules.InternalModule;
import marytts.features.FeatureComputer;
import marytts.features.FeatureMap;
import marytts.io.serializer.XMLSerializer;
import marytts.data.Utterance;
import marytts.data.Sequence;
import marytts.data.item.Item;

/**
 * The module which compute the features
 *
 * @author Marc Schr&ouml;der
 */
public class TargetFeatureLister extends InternalModule {

    /**
     * Default constructor
     *
     */
    public TargetFeatureLister() throws Exception {
        super("TargetFeatureLister", null);
        FeatureComputer.initDefault();
    }

    /**
     * The process method which take a MaryData object in parameter, compute the
     * features of the utterance referenced in the parameter and return a new
     * MaryData object which contains the reference to the updated utterance.
     *
     * @param d
     *            the input MaryData object
     * @return the MaryData object with the updated reference
     * @throws Exception
     *             [TODO]
     */
    public MaryData process(MaryData d) throws Exception {
        Utterance utt = d.getData();

        FeatureComputer the_feature_computer = FeatureComputer.the_feature_computer;

        listTargetFeatures(the_feature_computer, utt);
        // Second, construct targets
        MaryData result = new MaryData(d.getLocale(), utt);
        return result;
    }

    /**
     * Compute the features of a given utterance using a given feature computer
     *
     * @param the_feature_computer
     *            the feature computer used
     * @param utt
     *            the utterance to update
     * @throws Exception
     *             [TODO]
     */
    public void listTargetFeatures(FeatureComputer the_feature_computer,
                                   Utterance utt) throws Exception {

        Sequence<FeatureMap> target_features = new Sequence<FeatureMap>();
        Sequence<Item> items = (Sequence<Item>) utt.getSequence(SupportedSequenceType.PHONE);
        Set<String> keys = null;
        int i = 0;
        List<IntegerPair> list_pairs = new ArrayList<IntegerPair>();
        for (Item it : items) {
            FeatureMap map = the_feature_computer.process(utt, it);
            target_features.add(map);
            list_pairs.add(new IntegerPair(i, i));
            i++;
        }

        Relation rel_phone_features = new Relation(items, target_features, list_pairs);

        utt.addSequence(SupportedSequenceType.FEATURES, target_features);
        utt.setRelation(SupportedSequenceType.PHONE, SupportedSequenceType.FEATURES, rel_phone_features);
    }

    /**
     * Return directly the targets, and set in each target its feature vector
     *
     * @param the_feature_computer
     *            the feature computer used
     * @param utt
     *            the utterance used to compute the features
     * @param items
     *            the items whose features are going to be computed
     * @return a list of map of features corresponding of the given items
     * @throws Exception
     *             [TODO]
     */
    public List<FeatureMap> getListTargetFeatures(FeatureComputer the_feature_computer, Utterance utt,
            ArrayList<Item> items) throws Exception {
        List<FeatureMap> target_features = new ArrayList<FeatureMap>();

        for (Item it : items) {
            target_features.add(the_feature_computer.process(utt, it));
        }

        return target_features;
    }
}
