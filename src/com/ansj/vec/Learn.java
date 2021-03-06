package com.ansj.vec;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import love.cq.util.MapCount;

import com.ansj.vec.domain.HiddenNeuron;
import com.ansj.vec.domain.Neuron;
import com.ansj.vec.domain.WordNeuron;
import com.ansj.vec.util.Haffman;

public class Learn {

    private Map<String, Neuron> wordMap = new HashMap<>();
    /**
     * 训练多少个特征
     */
    private int layerSize = 200;

    /**
     * 上下文窗口大小
     */
    private int window = 5;

    private double sample = 1e-3;
    private double alpha = 0.025;

    public int EXP_TABLE_SIZE = 1000;

    private double[] expTable = new double[EXP_TABLE_SIZE];

    private int trainWordsCount = 0;

    private int MAX_EXP = 6;

    public Learn(Integer layerSize, Integer window, Double alpha, Double sample) {
        createExpTable();
        if (layerSize != null)
            this.layerSize = layerSize;
        if (window != null)
            this.window = window;
        if (alpha != null)
            this.alpha = alpha;
        if (sample != null)
            this.sample = sample;
    }

    public Learn() {
        createExpTable();

    }

    /**
     * trainModel
     * @throws IOException 
     */
    private void trainModel(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(file)))) {
            String temp = null;
            long nextRandom = 5;
            while ((temp = br.readLine()) != null) {
                String[] strs = temp.split(" ");
                List<WordNeuron> sentence = new ArrayList<WordNeuron>();
                for (int i = 0; i < strs.length; i++) {
                    Neuron entry = wordMap.get(strs[i]);
                    if (entry == null) {
                        continue;
                    }
                    // The subsampling randomly discards frequent words while keeping the ranking same
                    if (sample > 0) {
                        double ran = (Math.sqrt(entry.freq / (sample * trainWordsCount)) + 1)
                                     * (sample * trainWordsCount) / entry.freq;
                        nextRandom = nextRandom * 25214903917L + 11;
                        if (ran < (nextRandom & 0xFFFF) / (double) 65536) {
                            continue;
                        }
                        sentence.add((WordNeuron)entry);
                    }
                }
                nextRandom = nextRandom * 25214903917L + 11;
                for (int index = 0; index < sentence.size(); index++) {
                    skipGram( index,sentence,(int) nextRandom % window);
                }
               
            }
        }
    }

    /**
     * skip gram 模型训练
     * @param sentence
     * @param neu1 
     */
    private void skipGram(int index, List<WordNeuron> sentence, int b) {
        // TODO Auto-generated method stub
        //double[] neu1 = new double[layerSize];//神经元
        WordNeuron word = sentence.get(index);
        int a, c = 0;
        for (a = b; a < window * 2 + 1 - b; a++) {
            if (a == window) {
                continue;
            }
            c = index - window + a;
            if (c < 0 || c >= sentence.size()) {
                continue;
            }

            double[] neu1e = new double[layerSize];//误差项
            //HIERARCHICAL SOFTMAX
            List<Neuron> neurons = word.getNeurons();
            WordNeuron we = sentence.get(c);
            for (int i = 0; i < neurons.size(); i++) {
                HiddenNeuron out = (HiddenNeuron) neurons.get(i);
                double f = 0;
                // Propagate hidden -> output
                for (int j = 0; j < layerSize; j++) {
                    f += we.syn0[j] * out.syn1[j];
                }
                if (f <= -MAX_EXP || f >= MAX_EXP) {
                    continue;
                } else {
                    f = (f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2);
                    f = expTable[(int) f];
                }
                // 'g' is the gradient multiplied by the learning rate
                double g = (1 - word.codeArr[i] - f) * alpha;
                for (c = 0; c < layerSize; c++) {
                    neu1e[c] += g * out.syn1[c];
                }
                // Learn weights hidden -> output
                for (c = 0; c < layerSize; c++) {
                    out.syn1[c] += g * we.syn0[c];
                }
            }

            // Learn weights input -> hidden
            for (int j = 0; j < layerSize; j++) {
                we.syn0[j] += neu1e[j];
            }
        }

    }

    /**
     * 统计词频
     * @param file
     * @throws IOException
     */
    private void readVocab(File file) throws IOException {
        MapCount<String> mc = new MapCount<>();
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(file)))) {
            String temp = null;
            while ((temp = br.readLine()) != null) {
                String[] split = temp.split(" ");
                trainWordsCount += split.length;
                for (String string : split) {
                    mc.add(string);
                }
            }
        }
        for (Entry<String, Integer> element : mc.get().entrySet()) {
            wordMap.put(element.getKey(), new WordNeuron(element.getKey(), element.getValue(),
                layerSize));
        }
    }

    /**
     * Precompute the exp() table
     * f(x) = x / (x + 1)
     */
    private void createExpTable() {
        for (int i = 0; i < EXP_TABLE_SIZE; i++) {
            expTable[i] = Math.exp(((i / (double) EXP_TABLE_SIZE * 2 - 1) * MAX_EXP));
            expTable[i] = expTable[i] / (expTable[i] + 1);
        }
    }

    /**
     * 根据文件学习
     * @param file
     * @throws IOException 
     */
    public void learnFile(File file) throws IOException {
        readVocab(file);
        new Haffman(layerSize).make(wordMap.values());
        trainModel(file);
    }

    /**
     * 保存模型
     */
    public void saveModel(File file) {
        // TODO Auto-generated method stub

        try (DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(
            new FileOutputStream(file)))) {
            dataOutputStream.writeInt(wordMap.size());
            dataOutputStream.writeInt(layerSize);
            double[] syn0 = null;
            for (Entry<String, Neuron> element : wordMap.entrySet()) {
                dataOutputStream.writeUTF(element.getKey());
                syn0 = ((WordNeuron) element.getValue()).syn0;
                for (double d : syn0) {
                    dataOutputStream.writeFloat(((Double) d).floatValue());
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        Learn learn = new Learn();
        learn.learnFile(new File("xh.txt"));
        learn.saveModel(new File("javaVector"));
    }
}
