import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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

import org.apache.commons.io.FileUtils;

public class SubbasinSimulationParallel
{
	String FILE_NAME;
	List<Integer> computationNumbers;
	String RESULTS_FOLDER;
	int N_THREADS;
	int plotComputation;
	boolean deleteDirectory;
	
	public SubbasinSimulationParallel(String fileName, List<Integer> computationNumbers, int n_threads, int plotComputation, boolean deleteDirectory)
	{
		FILE_NAME = fileName;
		this.computationNumbers = computationNumbers;
		N_THREADS = n_threads;
		RESULTS_FOLDER = fileName.split("/")[1].split(".txt")[0] + "_" + computationNumbers.get(0);
		
		File dir = new File(RESULTS_FOLDER);
		if (dir.exists() && deleteDirectory) {
			deleteDir(dir);
		} 
		
		if (!dir.exists()) {
			boolean success = new File(RESULTS_FOLDER).mkdirs();
			if (!success) {
			    System.out.println("Directory creation failed");
			} else {
				new File(RESULTS_FOLDER + "/HRU").mkdirs();
				new File(RESULTS_FOLDER + "/Subbasin").mkdirs();
				System.out.println("Root directory has been created.");
			}	
		}
		
		this.plotComputation = plotComputation;
	}
    
	public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
                String[] children = dir.list();
                for (int i = 0; i < children.length; i++) {
                        boolean success = deleteDir(new File(dir, children[i]));
                        if (!success) {
                                return false;
                        }
                }
        }
        return dir.delete();
    }
    
	public void run() throws IOException, InterruptedException
	{
		if (incorrectNumber()) {
			return;
		}
		
		// Sorts the computations by Area
		Map<String, Float> sortedComputationAreaMap = getComputationsSortedByHRUArea();
		
		// Computes the reference map. A map of HRU number to the index it corresponds to
		Map<Integer, Integer> referenceMap = computeReferenceArray();
		
		// Based on the computation specified (in the input), the corresponding value is
		// picked from the list of computations
		List<Integer> computation = getComputation(sortedComputationAreaMap);
		
		// Gets the list of HRUs corresponding to each subbasin
		Map<Integer, List<Integer>> hrusForSubbasin = getHrusForSubbasin();

		System.out.println("Computing base case values ...");
		List<Integer> baseCaseValues = getBaseCaseValues(computation);
		generateInputFileForSubbasin(baseCaseValues, 0);
		int result = runOCMExecutable();
		if (result == -1){
			System.out.println("In use");
			return;
		}else if (result == -2){
			System.out.println("Failed");
			return;
		}
		
		String str;
		if (FILE_NAME.contains("bd")) {
			 str = "ocm/BigDitchWs/ClientRequestOutput.txt";	
		} else {
			str = "ocm/BigLongCreekWs/ClientRequestOutput.txt";
		}
		copyFile(str, RESULTS_FOLDER + "/result_output_0.txt");

		// Gets the base case values by parsing the result file generated by
		// the executable
		Map<String, Float> baseCaseResultValues = getBaseCaseResultValues();
		
		if (plotComputation == 1) {
			int m = N_THREADS;
			int n_temp = computationNumbers.size();
			int m_temp = N_THREADS;
			int j = 0;
			List<Thread> threads = new ArrayList<>();

			for (int i=1; i<=m; i++) {
				List<Integer> computationNumbersForThread = new ArrayList<>();			
				int temp = (int) Math.ceil((float)n_temp/m_temp);
				
				int k = 0;
				while (k < temp) {
					computationNumbersForThread.add(computationNumbers.get(j));
					j++;
					k++;
				}
				
				EntireComputationSimulation thread = new EntireComputationSimulation(FILE_NAME, 
																					 RESULTS_FOLDER, 
																					 computationNumbersForThread,
																					 sortedComputationAreaMap,
																					 i);
				
				n_temp -= temp;
				m_temp--;

				thread.start();
				threads.add(thread);
			}
			
			for (Thread t : threads ) {
				t.join();
			}
			
			System.out.println();
		} else {
			BufferedWriter resultWrite = new BufferedWriter(new FileWriter(RESULTS_FOLDER + "/result.csv"));
			resultWrite.write("Subbasin, SED, NIT, LED\n");
			resultWrite.close();

			List<Integer> subbasins = new ArrayList<>(hrusForSubbasin.keySet());
			int m = N_THREADS;
			int n_temp = subbasins.size();
			int m_temp = N_THREADS;
			int j = 0;
			List<Thread> threads = new ArrayList<>();

			for (int i=1; i<=m; i++) {
				List<Integer> subbasinForThread = new ArrayList<>();			
				int temp = (int) Math.ceil((float)n_temp/m_temp);
				
				int k = 0;
				while (k < temp) {
					subbasinForThread.add(subbasins.get(j));
					j++;
					k++;
				}
				
				EachSubbasinComputation thread = new EachSubbasinComputation(FILE_NAME,
																			 RESULTS_FOLDER,
																			 sortedComputationAreaMap,
																			 referenceMap,
																			 computation,
																			 hrusForSubbasin,
																			 baseCaseResultValues,
																			 subbasinForThread,
																			 i,
																			 plotComputation);
				
				n_temp -= temp;
				m_temp--;

				thread.start();
				threads.add(thread);
			}
			
			for (Thread t : threads ) {
				t.join();
			}	
		}
	}
	
	/**
	 * Copies the file from src to destination
	 * @param read
	 * @param write
	 * @throws IOException
	 */
	public void copyFile(String read, String write) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(read));
		BufferedWriter bw = new BufferedWriter(new FileWriter(write));
		String str;
		
		while ((str = br.readLine()) != null) {
			bw.write(str + "\n");
		}
		
		br.close();
		bw.close();
	}
	
	/**
	 * Gets the base case values (based on the computation of the executable)
	 * @return
	 * @throws IOException
	 */
	private Map<String, Float> getBaseCaseResultValues() throws IOException
	{
		BufferedReader br;
		String str;
		if (FILE_NAME.contains("bd")) {
			 br = new BufferedReader(new FileReader("ocm/BigDitchWs/ClientRequestOutput.txt"));	
		} else {
			 br = new BufferedReader(new FileReader("ocm/BigLongCreekWs/ClientRequestOutput.txt"));			
		}
		
		str = br.readLine();
		str = br.readLine();
		
		String[] strList = str.trim().split("( |\t)+");
		
		Map<String, Float> map = new HashMap<>();
		map.put("sed", Float.parseFloat(strList[0]));
		map.put("nit", Float.parseFloat(strList[1]));
		map.put("pho", Float.parseFloat(strList[2]));
		
		br.close();
		
		return map;
	}

	/**
	 * Input values for getting the base case values
	 * @param computation
	 * @return
	 */
	private List<Integer> getBaseCaseValues(List<Integer> computation)
	{
		List<Integer> finalVals = new ArrayList<>();
		
		for (int i = 0; i < computation.size(); i++) {
			finalVals.add(0);
		}
		
		return finalVals;
	}
	
	/*
	 * Helper method to run the OCM fortran executable.
	 */
	private int runOCMExecutable() {
		Process process = null;
		String workingDirectory;
		if (FILE_NAME.contains("bd")) {
			workingDirectory = "ocm/BigDitchWs/";
		} else {
			workingDirectory = "ocm/BigLongCreekWs/";
		}
		
		try {
			ProcessBuilder builder = new ProcessBuilder(workingDirectory +"OCM_AMGA2_IndBMP_LHS.exe");
			builder.directory(new File(workingDirectory));
			builder.redirectErrorStream(true);
			File output = new File(workingDirectory+"spark_ocm_output.txt");
			builder.redirectOutput(output);
			
			process = builder.start();
		} catch (Exception e) {
			System.out.println("Error: " + workingDirectory +  e.getMessage());
			e.printStackTrace();
			return -1;
		}

		try {
			//System.out.println("Execution Started : " + (new Date()).toString());
			String result = null;
			process.waitFor();
			//System.out.println("Execution completed : " + (new Date()).toString());
			result = FileUtils.readFileToString(new File(workingDirectory + "spark_ocm_output.txt"));
			if (result == null || !result.contains("Execution successfully completed")){
				//System.out.println("***Execution Failed***");
				return -2;
			}
			else{
				//System.out.println("***Execution Successful***");
			}
		} catch (Exception e) {
			// Handle exception that could occur when waiting
			// for a spawned process to terminate
			//System.out.println("***Exception Occurred***");
			e.printStackTrace();
			return -2;
		}
		return 0;
	}
	
	/**
	 * Generates the input file for the subbasin (to be used by the model) and places a copy
	 * in the results folder
	 * 
	 * @param finalVals
	 * @param subbasinId
	 * @throws IOException
	 */
	public void generateInputFileForSubbasin(List<Integer> finalVals, int subbasinId) throws IOException
	{
		BufferedWriter bw, bwCopy = new BufferedWriter(new FileWriter(RESULTS_FOLDER + "/result_input_" + subbasinId + ".txt"));
		if (FILE_NAME.contains("bd")) {
			bw = new BufferedWriter(new FileWriter("ocm/BigDitchWs/Client_Input.txt"));
			bw.write("1\n");
			bw.write("1\n");
			bwCopy.write("1\n");
			bwCopy.write("1\n");
		} else {
			bw = new BufferedWriter(new FileWriter("ocm/BigLongCreekWs/Client_Input.txt"));
			bw.write("1\n");
			bw.write("2\n");
			bwCopy.write("1\n");
			bwCopy.write("2\n");
		}
		
		for (int val : finalVals) {
			bw.write(val + "\n");
			bwCopy.write(val + "\n");
		}
		
		bw.close();
		bwCopy.close();
	}

	/**
	 * Generates the input file for the subbasin (to be used by the model) and places a copy
	 * in the results folder
	 * 
	 * @param finalVals
	 * @param subbasinId
	 * @throws IOException
	 */
	public void generateEntireComputationInputFile(List<Integer> finalVals) throws IOException
	{
		BufferedWriter bw, bwCopy = new BufferedWriter(new FileWriter(RESULTS_FOLDER + "/result_input_entire_computation.txt"));
		if (FILE_NAME.contains("bd")) {
			bw = new BufferedWriter(new FileWriter("ocm/BigDitchWs/Client_Input.txt"));
			bw.write("1\n");
			bw.write("1\n");
			bwCopy.write("1\n");
			bwCopy.write("1\n");
		} else {
			bw = new BufferedWriter(new FileWriter("ocm/BigLongCreekWs/Client_Input.txt"));
			bw.write("1\n");
			bw.write("2\n");
			bwCopy.write("1\n");
			bwCopy.write("2\n");
		}
		
		for (int val : finalVals) {
			bw.write(val + "\n");
			bwCopy.write(val + "\n");
		}
		
		bw.close();
		bwCopy.close();
	}
	
	/**
	 * Returns a map of subbasin to the HRU's that correspond to it
	 * @return
	 * @throws IOException
	 */
	private Map<Integer, List<Integer>> getHrusForSubbasin() throws IOException
	{
		BufferedReader br;
		if (FILE_NAME.contains("bd")) {
			br = new BufferedReader(new FileReader("src/BigDitch_SHA.txt"));				 
		} else {
			br = new BufferedReader(new FileReader("src/BigLongC_SHA.txt"));				 
		}

		String str;
		String[] strList;
		Map<Integer, List<Integer>> subbasinHRUsMap = new LinkedHashMap<>();

		str = br.readLine();
		while ((str = br.readLine()) != null) {
			strList = str.split("( |\t)+");
			
			if (plotComputation == 2) {
				if (!subbasinHRUsMap.containsKey(Integer.parseInt(strList[0]))) {
					subbasinHRUsMap.put(Integer.parseInt(strList[0]), new ArrayList<Integer>());
				}
				
				subbasinHRUsMap.get(Integer.parseInt(strList[0])).add(Integer.parseInt(strList[1]));				
			} else {
				if (!subbasinHRUsMap.containsKey(Integer.parseInt(strList[1]))) {
					subbasinHRUsMap.put(Integer.parseInt(strList[1]), new ArrayList<Integer>());
				}
				
				subbasinHRUsMap.get(Integer.parseInt(strList[1])).add(Integer.parseInt(strList[1]));
			}
		}
		
		br.close();
		
		return subbasinHRUsMap;
	}

	/**
	 * Gets a particular computation from the sorted list of computations (based on the
	 * input of the user)
	 * @param sortedComputationAreaMap
	 * @return
	 */
	private List<Integer> getComputation(Map<String, Float> sortedComputationAreaMap)
	{
		String s = "";
		int count = 0;
		
		for (Entry<String, Float> e : sortedComputationAreaMap.entrySet()) {
			count++;
			if (count == computationNumbers.get(0)) {
				s = e.getKey();
			}
		}
		
		String[] strList = s.split("( |\t)+");
		
		// Reference array computed here
		List<Integer> arr = new ArrayList<>();
		for (int i=0; i<strList.length - 6; i++) {
			arr.add(Integer.parseInt(strList[i]));
		}
		
		return arr;
	}

	public boolean incorrectNumber() throws IOException
	{
		
		BufferedReader br = new BufferedReader(new FileReader(FILE_NAME));
		int nlines = 0;
		String str;
		
		while ((str = br.readLine()) != null && !str.contains("#REF!")) {
			nlines++;
		}

		br.close();
		
		if (computationNumbers.get(0) > (nlines - 1)) {
			System.out.println("Total number of  computations are : " + (nlines - 1) + ". Please enter an appropriate number.");
			return true;
		} else {
			return false;
		}
	}
	
	public Map<Integer, Integer> computeReferenceArray() throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(FILE_NAME));
		String reference = br.readLine();
		br.close();
		
		String[] strList = reference.split("( |\t)+");
		
		// Reference array computed here
		Map<Integer, Integer> referenceMap = new HashMap<>();
		for (int i=0; i<strList.length - 6; i++) {
			referenceMap.put(Integer.parseInt(strList[i]), i);
		}
		
		return referenceMap;
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
	
	public void simulate() throws NumberFormatException, IOException, InterruptedException
	{
		run();	
	}
}