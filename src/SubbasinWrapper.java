import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class SubbasinWrapper
{
	public static void main(String[] args) throws NumberFormatException, IOException
	{
		BufferedReader br = new BufferedReader(new FileReader("src/simulation_input.txt"));
		String str;
		String[] strList;
		
		while ((str = br.readLine()) != null) {
			strList = str.split(" ");
			
			SubbasinSimulation s = new SubbasinSimulation(strList[0], Integer.parseInt(strList[1]));
			s.simulate();
		}
		
		br.close();
	}
}
