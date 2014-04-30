import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GenerateResultsFile
{
	private void run() throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader("src/folders.txt"));
		
		String str;
		
		while ((str = br.readLine()) != null) {
			File root = new File(str);
			File[] listFiles = root.listFiles();
			
			Map<String, Float> baseCaseResultValues = getBaseCaseResultValues(root.getName(), 0);
			
			BufferedWriter resultWrite = new BufferedWriter(new FileWriter(root.getName() + "/result_" + root.getName() + ".csv"));
			resultWrite.write("Subbasin, SED, NIT, LED\n");

			for (File f : listFiles) {
				if (f.getName().contains("output")) {
					String temp = f.getName().split("\\.")[0];
					int number = Integer.parseInt(temp.substring(14));
					
					if (number != 0) {
						Map<String, Float> resultValues = getBaseCaseResultValues(root.getName(), number);
						Float val1 = (baseCaseResultValues.get("sed") - resultValues
								.get("sed")) / baseCaseResultValues.get("sed") * 100;
						Float val2 = (baseCaseResultValues.get("nit") - resultValues
								.get("nit")) / baseCaseResultValues.get("nit") * 100;
						Float val3 = (baseCaseResultValues.get("pho") - resultValues
								.get("pho")) / baseCaseResultValues.get("pho") * 100;
						resultWrite.write(number + "," + val1 + "," + val2 + ","
								+ val3 + "\n");
					}
				}
			}

			resultWrite.close();
		}
		
		br.close();
	}
	
	/**
	 * Gets the base case values (based on the computation of the executable)
	 * @return
	 * @throws IOException
	 */
	private Map<String, Float> getBaseCaseResultValues(String root, int i) throws IOException
	{
		String str;
		BufferedReader br = new BufferedReader(new FileReader(root + "/result_output_" + i + ".txt"));	
		
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
	
	public static void main(String[] args) throws IOException
	{
		GenerateResultsFile main = new GenerateResultsFile();
		main.run();
	}
}
