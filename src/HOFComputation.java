import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class HOFComputation
{
	int TOTAL_COMPUTATIONS;
	String FILE_NAME;
	
	private void run(String fileName, int computationsCount, boolean useDecay) throws IOException
	{
		FILE_NAME = fileName;
		TOTAL_COMPUTATIONS = computationsCount;
		
		BufferedReader br = new BufferedReader(new FileReader(FILE_NAME));
		int nlines = 0;
		String str;
		
		while ((str = br.readLine()) != null && !str.contains("#REF!")) {
			nlines++;
		}

		br.close();
		
		if (TOTAL_COMPUTATIONS > (nlines - 1)) {
			System.out.println("Total number of  computations are : " + (nlines - 1) + ". Please enter an appropriate number.");
			return;
		}
		
		
		Map<String, Float> sortedComputationAreaMap = getComputationsSortedByHRUArea();
		
		// Truncated by a user specified set of lines
		Map<String, Float> trncatedSortedComputationAreaMap = truncateMap(sortedComputationAreaMap);
		
		writeFinalComputationsToFile(trncatedSortedComputationAreaMap);
		
		Map<Integer, Float> hofs;
		if (useDecay) {
			hofs = computeHOFWithDecay(new ArrayList<String>(trncatedSortedComputationAreaMap.keySet()));	
		} else {
			hofs = computeHOF(new ArrayList<String>(trncatedSortedComputationAreaMap.keySet()));			
		}
		
		writeHOFsToFile(hofs);
		
		Map<Integer, Float> sofs = computeSOFs(new HashMap<>(hofs));
		
		writeSOFsToFile(sofs);
	}

	private Map<Integer, Float> computeSOFs(Map<Integer, Float> hofs) throws IOException
	{
		BufferedReader br;
		if (FILE_NAME.contains("bd")) {
			 br = new BufferedReader(new FileReader("BigDitch_SHA.txt"));	
		} else {
			 br = new BufferedReader(new FileReader("BigLongC_SHA.txt"));			
		}

		String str;
		String[] strList;
		Map<Integer, List<Integer>> subbasinHRUsMap = new LinkedHashMap<>();
		Map<Integer, Float> subbasinArea = new HashMap<>();
		Map<Integer, Float> hruArea = new HashMap<>();
		Map<Integer, Float> sofs = new LinkedHashMap<>();

		str = br.readLine();
		while ((str = br.readLine()) != null) {
			strList = str.split("( |\t)+");
			
			if (!subbasinHRUsMap.containsKey(Integer.parseInt(strList[0]))) {
				subbasinHRUsMap.put(Integer.parseInt(strList[0]), new ArrayList<Integer>());
				subbasinArea.put(Integer.parseInt(strList[0]), 0F);
			}
			
			subbasinHRUsMap.get(Integer.parseInt(strList[0])).add(Integer.parseInt(strList[1]));
			subbasinArea.put(Integer.parseInt(strList[0]), subbasinArea.get(Integer.parseInt(strList[0])) + Float.parseFloat(strList[2]));
			hruArea.put(Integer.parseInt(strList[1]), Float.parseFloat(strList[2]));
		}
		
		for (Entry<Integer, List<Integer>> e : subbasinHRUsMap.entrySet()) {
			float answer = 0F;
			for (Integer hru : e.getValue()) {
				if (hofs.get(hru) != null) {
					answer += hofs.get(hru) * hruArea.get(hru);
				}
			}
			answer /= subbasinArea.get(e.getKey());
			sofs.put(e.getKey(), answer);
		}
		
		br.close();
		
		return sofs;
	}

	public void writeHOFsToFile(Map<Integer, Float> hofs) throws IOException
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter("hofs.csv"));
		
		bw.write("HRU No, HOF value\n");
		
		for (Entry<Integer, Float> e : hofs.entrySet()) {
			bw.write((e.getKey() + "," + e.getValue()) + "\n");
		}
		
		bw.close();
	}
	
	public void writeSOFsToFile(Map<Integer, Float> hofs) throws IOException
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter("sofs.csv"));
		
		bw.write("Subbasin No, SOF value\n");
		
		for (Entry<Integer, Float> e : hofs.entrySet()) {
			bw.write(e.getKey() + "," + e.getValue() + "\n");
		}
		
		bw.close();
	}
	
	public void writeFinalComputationsToFile(Map<String, Float> computations) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(FILE_NAME));
		String reference = br.readLine();
		br.close();
		
		BufferedWriter bw = new BufferedWriter(new FileWriter("computations.txt"));
		
		bw.write(reference + "\n");
		
		for (Entry<String, Float> e : computations.entrySet()) {
			bw.write(e.getKey() + "\n");
		}
		
		bw.close();
	}	
	
	private Map<Integer, Float> computeHOF(List<String> trncatedSortedComputationList) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(FILE_NAME));
		String reference = br.readLine();
		br.close();
		
		String[] strList = reference.split("( |\t)+");
		List<Integer> referenceArray = new ArrayList<>();
		for (int i=0; i<strList.length - 6; i++) {
			referenceArray.add(Integer.parseInt(strList[i]));
		}
		
		Map<Integer, Float> countHRUs = new HashMap<>();
		
		int i=0;
		for (; i<trncatedSortedComputationList.size() - 1; i++) {
			strList = trncatedSortedComputationList.get(i).split("( |\t)+");
			for (int j=0; j<strList.length - 6; j++) {
				if (!countHRUs.containsKey(referenceArray.get(j))) {
					countHRUs.put(referenceArray.get(j), 0F);
				}
				
				if (!strList[j].trim().equals("0")) {
					countHRUs.put(referenceArray.get(j), countHRUs.get(referenceArray.get(j)) + 1);					
				}
			}
		}
		
		strList = trncatedSortedComputationList.get(i).split("( |\t)+");
		for (int j=0; j<strList.length - 6; j++) {
			if (!countHRUs.containsKey(referenceArray.get(j))) {
				countHRUs.put(referenceArray.get(j), 0F);
			}
			
			if (!strList[j].trim().equals("0")) {
				countHRUs.put(referenceArray.get(j), ((countHRUs.get(referenceArray.get(j)) + 1)/ TOTAL_COMPUTATIONS));						
			} else {
				countHRUs.put(referenceArray.get(j), 0F);		
			}
		}
		
		return sortByValuesDesc(countHRUs);
	}

	private Map<Integer, Float> computeHOFWithDecay(List<String> trncatedSortedComputationList) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(FILE_NAME));
		String reference = br.readLine();
		br.close();
		
		String[] strList = reference.split("( |\t)+");
		List<Integer> referenceArray = new ArrayList<>();
		for (int i=0; i<strList.length - 6; i++) {
			referenceArray.add(Integer.parseInt(strList[i]));
		}
		
		Map<Integer, Float> countHRUs = new HashMap<>();
		
		int i=0;
		for (; i<trncatedSortedComputationList.size() - 1; i++) {
			strList = trncatedSortedComputationList.get(i).split("( |\t)+");
			for (int j=0; j<strList.length - 6; j++) {
				if (!countHRUs.containsKey(referenceArray.get(j))) {
					countHRUs.put(referenceArray.get(j), 0F);
				}
				
				if (!strList[j].trim().equals("0")) {
					countHRUs.put(referenceArray.get(j), countHRUs.get(referenceArray.get(j)) + ((float)TOTAL_COMPUTATIONS - i)/TOTAL_COMPUTATIONS);					
				}
			}
		}
		
		return sortByValuesDesc(countHRUs);
	}
	
	private Map<String, Float> truncateMap(Map<String, Float> sortedComputationAreaMap)
	{
		Map<String, Float> trncatedSortedComputationAreaMap = new LinkedHashMap<>();
		int i = 0;
		
		for (Entry<String, Float> e : sortedComputationAreaMap.entrySet()) {
			trncatedSortedComputationAreaMap.put(e.getKey(), e.getValue());
			
			i++;
			if (i == TOTAL_COMPUTATIONS) {
				break;
			}
		}
		
		return trncatedSortedComputationAreaMap;
	}

	public Map<String, Float> getComputationsSortedByHRUArea() throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(FILE_NAME));
		String str = br.readLine();
		String[] strList = str.split("( |\t)+");
		int length = strList.length - 6;
		Map<String, Float> computationAreaMap = new HashMap<>();
		
		while ((str = br.readLine()) != null && !str.contains("#REF!")) {
			strList = str.split("( |\t)+");
			computationAreaMap.put(str, Float.parseFloat(strList[length + 2]));
		}
		
		br.close();
		
		return sortByValues(computationAreaMap);
	}
	
	public static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map) {
	    Comparator<K> valueComparator =  new Comparator<K>() {
	        public int compare(K k1, K k2) {
	            int compare = map.get(k1).compareTo(map.get(k2));
	            if (compare == 0) return 1;
	            else return compare;
	        }
	    };
	    Map<K, V> sortedByValues = new TreeMap<K, V>(valueComparator);
	    sortedByValues.putAll(map);
	    return sortedByValues;
	}
	
	public static <K, V extends Comparable<V>> Map<K, V> sortByValuesDesc(final Map<K, V> map) {
	    Comparator<K> valueComparator =  new Comparator<K>() {
	        public int compare(K k1, K k2) {
	            int compare = map.get(k2).compareTo(map.get(k1));
	            if (compare == 0) return 1;
	            else return compare;
	        }
	    };
	    Map<K, V> sortedByValues = new TreeMap<K, V>(valueComparator);
	    sortedByValues.putAll(map);
	    return sortedByValues;
	}
	
	public static void main(String[] args) throws IOException
	{
		HOFComputation main = new HOFComputation();
		if (args[2].equals("1")) {
			main.run(args[0], Integer.parseInt(args[1]), true);	
		} else {
			main.run(args[0], Integer.parseInt(args[1]), false);
		}
	}
}
