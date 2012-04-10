/**
 * Copyright 2002 DFKI GmbH.
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
package marytts.language.sv;

import marytts.language.sv.SplitItem;
import marytts.server.MaryProperties;
import marytts.fst.FSTLookup;
import marytts.exceptions.MaryConfigurationException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

/**
 * Splits up compound words into smaller parts, returning the pronunciation. Every part must have at least 
 * four letters. This does not apply to the joint element 's' and to inflections that may appear at the end
 * of a word. The pronunciation for word parts are of course taken from the lexicon. Pronunciations for 
 * inflections are taken from sv_SE_inflections.txt if any is given for the inflection in question. The fallback
 * is the LTS-rules.  
 *  
 * @author Erik Sterneberg
 *
 */
public class sv_CompoundSplitter {
	protected FSTLookup lexicon;
	public HashMap<String, String> inflection_endings;

	public sv_CompoundSplitter() throws IOException,  MaryConfigurationException{

	    //HB 120323 some problem with this..
	    String endings_path = endingsPath= MaryProperties.needFilename("sv.inflections");
		this.inflection_endings = new HashMap<String, String>();
		this.lexicon = new FSTLookup(MaryProperties.needFilename("sv.lexicon"));
		
		try{
			BufferedReader infile = new BufferedReader(new FileReader(endings_path));
			String line;
			String inf = null;
			String phon = null;

			while ((line = infile.readLine()) != null){

				try{
					if (Character.isLetter(line.charAt(0))){

						String[] inf_phon = line.trim().split("\t");
						inf= inf_phon[0];
						phon = inf_phon[1];

						if (phon!=null)
							this.inflection_endings.put(inf, phon);
						else
							this.inflection_endings.put(inf, "");						
					}
				}
				catch (Exception e){
					//System.err.println("1.1. Error: " + e.getMessage() + ". inf and phon are: '" + inf + "' and '" + phon + "'");					
				}	
			}

		}
		catch (Exception e){
			System.err.println("1. Error: " + e.getMessage());
		}

		String[] vowels = "e y u i o å a ö ä".split(" ");		
	}
	
	/**
	 * Tries to split a compound, returning the best candidate. Every substring in the word that appears in the lexicon
	 * is put into the hashmap 'parts'. Then, all possible combinations of these parts (plus joint elements and inflections)
	 * are used to come up with possible splits. Then the best split is chosen and returned. 
	 * 
	 * @param s String to split
	 * @return ArrayList<String[]>
	 */
	protected ArrayList<String[]> splitCompound(String s){	
		String[] candidate = new String[3];
		s = s.trim();

		HashMap<Integer, ArrayList<String[]>> parts = new HashMap<Integer, ArrayList<String[]>>();
		ArrayList<SplitItem> queue = new ArrayList<SplitItem>();
		ArrayList<SplitItem> candidates = new ArrayList<SplitItem>();

		// Fill hashmap parts with parts from lexicon and inflection list
		for (int start=0; start < s.length(); start++){
			for (int end=s.length(); end > -1; end--){
				if (end > start){
					if (inflection_endings.containsKey(s.substring(start, end))
							&& end == s.length() ){ // An inflection must appear at the end of a word						
						if (! parts.containsKey(start)){
							parts.put(start, new ArrayList<String[]>());
						}
						parts.get(start).add(new String[]{Integer.toString(end) ,s.substring(start, end), "INFL"});
						//System.out.println("Putting part '" + s.substring(start, end) + "' at index " + start + " (inflection)");
					}

					else if (s.substring(start, end).length() > 3 && lexicon.lookup(s.substring(start, end)).length > 0){					
						if (! parts.containsKey(start)){
							parts.put(start, new ArrayList<String[]>());							
						}					
						parts.get(start).add(new String[]{Integer.toString(end), s.substring(start, end), "LEX"});					
						//System.out.println("Putting part '" + s.substring(start, end) + "' at index " + start + " (lexicon)");
					}

					// If the substring == 's' and there is at least one syllable (read: one vowel) before and one after the s
					// it is to be considered a joint element
					else if(s.substring(start, end).equals("s")){
						String prev = s.substring(0, start);
						String aft = s.substring(end, s.length());
						Pattern pattern = Pattern.compile("[eyuioåaöä]");
						Matcher matcherPrev = pattern.matcher(prev);
						Matcher matcherAft = pattern.matcher(aft);

						if (matcherPrev.find() & matcherAft.find()){
							if (! parts.containsKey(start)){
								parts.put(start, new ArrayList<String[]>());
							}
							parts.get(start).add(new String[]{Integer.toString(end), s.substring(start, end), "JOI"});
						}
					}
					else{
						//System.out.println("The lexicon did not contain the string: " + s.substring(start, end));
					}
				}
			}
		}

		// Add one startitem to queue
		queue.add(new SplitItem(s, 0));

		// While queue is not empty, make new candidates
		while (queue.size() > 0){
			SplitItem currentItem = queue.get(0);
			try{
				queue.remove(0);
			}
			catch (Exception e){}

			makeCandidates(currentItem, queue, s, candidates, parts);
		}

		// Choose the best candidate, make it into a ArrayList such as ([Tyskland, LEX], [s, INFL], [semester, LEX]) and return it
		
		ArrayList<String[]> bestCandidate;
		try {
			bestCandidate = chooseBestCandidate(candidates);
		}
		catch (Exception e){
			bestCandidate = new ArrayList<String[]>();
		}

		// For debugging
		/*
		System.out.println("---------- Parts of the best compound split:");
		for (String[] part: bestCandidate){
			System.out.println(part[0] + "\t" + part[1]);
		}
		System.out.println("-------------------------------------------");
		*/

		return bestCandidate;
	}

	/**
	 * Chooses the best candidate split out of potentially several. The best candidate is always
	 * the one with the fewest parts (but > 1). No sophisticated method is applied to choose 
	 * between these candidates if there are several. Instead the first one is simply returned.
	 * 
	 * @param candidates
	 * @return
	 */
	private ArrayList<String[]> chooseBestCandidate(ArrayList<SplitItem> candidates){
		SplitItem temp = candidates.get(0);
		for (int i=1; i < candidates.size(); i++){
			if (candidates.get(i).parts.size() < temp.parts.size()){
				temp = candidates.get(i);
			}
		}

		ArrayList<String[]> bestCandidate = new ArrayList<String[]>();
		for (int i=0; i < temp.parts.size(); i++){
			String[]part = {temp.parts.get(i)[1], temp.parts.get(i)[2]};
			bestCandidate.add(part);			
		}		

		return bestCandidate;
	}
	
	/**
	 * Used to generate candidate splits for a potential compound word.
	 * 
	 * @param item
	 * @param queue
	 * @param inputword
	 * @param candidates
	 * @param parts
	 */
	private void makeCandidates(SplitItem item, ArrayList<SplitItem> queue, String inputword, ArrayList<SplitItem> candidates, HashMap<Integer, ArrayList<String[]>> parts){
		String s = item.string;
		Integer start = item.start;
		ArrayList<String[]> candidate_parts = item.parts;
		SplitItem currentSplitItem = item;

		 
		// If there are no words in the dictionary starting at the current 
		// starting index, then the candidate is finished building.		 
		if (start.equals(inputword.length())){
			try {
				if (Integer.parseInt(candidate_parts.get(0)[0])  - candidate_parts.get(0)[1].length() == 0){	
					candidates.add(new SplitItem(s, start, candidate_parts));
					/*
					System.out.println("ADDING CANDIDATE!");

					for (int i=0; i<candidate_parts.size(); i++){
						System.out.println(candidate_parts.get(i)[1] + "_" + candidate_parts.get(i)[2]);				
					}
					 */
				}
			}
			catch (Exception e){
				System.err.println("3. Error: " + e.getMessage());
			}
		}
		
		// Otherwise add new candidates to queue according to what parts 
		// are found in the hashmap 'parts'		 
		else {
			ArrayList<String[]> parts_at_current_index = parts.get(start);

			try {
				for (int i=0; i < parts_at_current_index.size(); i++){
					queue.add(new SplitItem(currentSplitItem, parts_at_current_index.get(i)));
				}
			}
			catch (Exception e){
				//System.err.println("4. Error: " + e.getMessage());
			}						
		}
	}
}
