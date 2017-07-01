package fpgrowth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.SequenceFile.Reader;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.ReflectionUtils;
import fpgrowth.utils.HDFSUtils;
import fpgrowth.utils.IdentityUtils;

public class FPGrowth {

	private Configuration conf = null;

	// 频繁一项集生成
	public String frequency_one_itemset_gen(String input, String minSupport) {
		String output = HDFSUtils.HDFS_TEMP_INPUT_URL + IdentityUtils.generateUUID();
		String[] inputArgs = new String[] { input, output, minSupport };
		FrequencyOneItemSet.main(inputArgs);
		return output;
	}

	// 频繁一项集排序
	public String frequency_one_itemset_sort(String input) {
		Map<String, Integer> map = new HashMap<String, Integer>();
		SequenceFile.Reader reader = null;
		try {
			Path dirPath = new Path(input);
			Path[] paths = HDFSUtils.getPathFiles(conf, dirPath);
			reader = new SequenceFile.Reader(conf, Reader.file(paths[0]));
			Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
			IntWritable value = new IntWritable();
			while (reader.next(key, value)) {
				map.put(key.toString(), value.get());
				key = new Text();
				value = new IntWritable();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(reader);
		}
		List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>();
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			entries.add(entry);
		}
		// 根据出现频次排序项
		Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
			public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
				return ((Integer) o2.getValue()).compareTo((Integer) o1.getValue());
			}
		});
		String output = HDFSUtils.HDFS_TEMP_INPUT_URL + IdentityUtils.generateUUID() + File.separator
				+ IdentityUtils.generateUUID();
		SequenceFile.Writer writer = null;
		try {
			Path path = new Path(output);
			writer = SequenceFile.createWriter(conf, Writer.file(path), Writer.keyClass(Text.class),
					Writer.valueClass(IntWritable.class), Writer.compression(CompressionType.NONE));
			for (Map.Entry<String, Integer> entry : entries) {
				writer.append(new Text(entry.getKey()), new IntWritable(entry.getValue()));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(writer);
		}
		return output;
	}

	// 频繁项集生成
	public void frequency_itemset_gen(String input, String output, String sort_input) {
		System.out.println("Frequency Itemset Gen Input: " + input);
		System.out.println("Frequency Itemset Gen Sort Input: " + sort_input);
		String[] inputArgs = new String[] { input, output, sort_input };
		FrequencyItemsetGen.main(inputArgs);
	}

	public void run(String[] args) {
		if (null == conf)
			conf = new Configuration();
		try {
			String[] inputArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
			if (inputArgs.length != 3) {
				System.err.println("Usage: 1. input path 2. output path 3. min support.");
				System.exit(2);
			}
			String output = frequency_one_itemset_gen(inputArgs[0], inputArgs[2]);
			String sort_output = frequency_one_itemset_sort(output);
			frequency_itemset_gen(inputArgs[0], inputArgs[1], sort_output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		FPGrowth job = new FPGrowth();
		long startTime = System.currentTimeMillis();
		job.run(args);
		long endTime = System.currentTimeMillis();
		System.out.println("spend time: " + (endTime - startTime));
	}
}