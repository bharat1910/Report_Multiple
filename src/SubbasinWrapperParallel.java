import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SubbasinWrapperParallel
{
	public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException
	{
		Long start = System.currentTimeMillis();
		
		BufferedReader br = new BufferedReader(new FileReader("src/simulation_input.txt"));
		String str;
		String[] strList;

		boolean deleteDirectory;
		if (Integer.parseInt(br.readLine().trim().split(" ")[0]) == 1) {
			deleteDirectory = true;
		} else {
			deleteDirectory = false;
		}
		
		int plotComputation = Integer.parseInt(br.readLine().trim().split(" ")[0]);
		
		int n_threads = Integer.parseInt(br.readLine().trim().trim().split(" ")[0]);
		
		while ((str = br.readLine()) != null) {
			strList = str.split(" ");
			
			List<Integer> computations = new ArrayList<>();
			for (int i=1; i<strList.length; i++) {
				computations.add(Integer.parseInt(strList[i]));
			}
			
			SubbasinSimulationParallel s = new SubbasinSimulationParallel(strList[0], computations, n_threads, plotComputation, deleteDirectory);
			s.simulate();
		}
		
		br.close();
		
		Long end = System.currentTimeMillis();
		
		System.out.println();
		System.out.println("Time taken for the computation : " + (((double) end - start)/1000));
	}
}
