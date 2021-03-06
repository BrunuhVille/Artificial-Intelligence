package smo;

import java.util.Arrays;
import java.util.Random;
import java.util.Vector;

public class DE {
	private double[][][] population;
	private int population_size;
	private int features_size;
	private Random random;
	private double tol;
	private String kernel;
	//random.nextdouble;
	private double[] x_l;
	private double[] x_u;
	//变异的大小设定，实数，F∈[0,1]
	private double F;
	//交叉概率
	private double CR;
	//最大迭代次数
	private int maxiter;
	//期望的目标的个体下标
	private double[][] tar;
	private double distance=999.9;
	
	private double[][][] train_data_x;
	private Integer[][] train_data_y;
	
	private double[][][] validation_data_x;
	private Integer[] validation_data_y;
	
	private double cost[];
	//private boolean flag = true;
	private double P;
	private double p_d;
	private int first_in_i;
	
	private double[][][] x1;
	private Integer[] y1;
	private double sum_training_data;
	private NBSVM_SMO[] ovo;
	
	//多分类
	public DE(double[][][] train_data_x,Integer[][] train_data_y,double[][][] validation_data_x,Integer[] validation_data_y,double tol,double[] x_l,double[] x_u, double[][][] x1, Integer[] y1) {
		random  = new Random();
		this.train_data_x = train_data_x;
		this.train_data_y = train_data_y;
		this.validation_data_x = validation_data_x;
		this.validation_data_y = validation_data_y;
		this.population_size =40;
		this.cost = new double[population_size];
		this.maxiter = 100;
		System.out.println(maxiter);
		this.tol = tol;
		this.x_l = x_l;
		this.x_u = x_u;
		this.features_size = x_l.length;
		this.population = new double[train_data_x.length][population_size][features_size];
		this.first_in_i = -1;
		this.x1 = x1;
		this.y1 = y1;
		this.ovo = new NBSVM_SMO[train_data_x.length];
		init();
		find();
	}
	
	
	public double[][] get_result() {
		return tar;
	}
	
	private void init() {
		for(int i=0;i<validation_data_x.length;i++) {
			sum_training_data+=validation_data_x[i].length;
		}
		for(int k=0;k<train_data_x.length;k++)
			for(int i=0;i<population_size;i++)
				for(int j=0;j<features_size;j++) {
					if(j==4||j==6) {
						population[k][i][j] =random.nextInt((int)x_u[j]+1)-random.nextInt((int)x_l[j]+1);
						continue;
					}
					population[k][i][j] = x_l[j]+random.nextDouble()*(x_u[j]-x_l[j]);
				}
	}
	
	private void find() {
		//通过变异和交叉，选择来选择最优参数；
		int iter=0;
		double percent;
		double[][] gene = new double [train_data_x.length][];
		double temp_cost;
		double[][] temp_gene = new double [train_data_x.length][];
		boolean s_gt=true;
		boolean in_s;
		int g_t=-1;
		int flag;
		//论文中没有第0代,因为我们将maxiters缩小了100倍，所以t也缩小100倍
		int T = Math.round((float)0.1*population_size-1);
		
		
//		T=3*T;
		
		
		double[] SR_T = new double[maxiter];
		double[] SR_G = new double[maxiter];
		double sum;
		for(int i=0;i<SR_T.length;i++) {
			if(i<T+1)SR_T[i] = 0;
			else SR_T[i] = 0.1;
		}
		//初始化fitness
		for(int i=0;i<population_size;i++) {
			for(int m=0;m<train_data_x.length;m++)gene[m] = population[m][i];
			cost[i] = fitness(gene);
		}
		
		//除了到达迭代上限外，还不知道怎么决定另外一个跳出循环的条件
		while(iter<maxiter) {
			if((iter+1)%20==0)
				System.out.println("第："+(iter+1));
			if(iter>=T) {
				if(s_gt=false)break;
				else {
					boolean all=true;
					for(int i=iter-T;i<iter;i++)if(SR_G[i]>SR_T[i]) {all=false;break;}
					if(all) {g_t = iter;s_gt=false;}
				}
			}
			if(s_gt==false&&iter==g_t)s_gt=true;
			//flag=false;
			//对基因和fitness从好到坏选择排序
			for(int i=0;i<cost.length-1;i++) {
				flag=i;
				for(int j=i+1;j<cost.length;j++) {
					if(cost[j]<cost[flag])flag=j;
				}
				temp_cost = cost[i];
				cost[i] = cost[flag];
				cost[flag] = temp_cost;
				for(int m=0;m<train_data_x.length;m++)temp_gene[m] = population[m][flag];
				for(int m=0;m<train_data_x.length;m++)population[m][flag] = population[m][i];
				for(int m=0;m<train_data_x.length;m++)population[m][i] = temp_gene[m];
				if(i==0&&iter==0) {
					distance=temp_cost;
					tar=temp_gene.clone();
				}
			}
//			System.out.println(Arrays.toString(cost));
			sum=0;
			P = 0.1+0.9*Math.pow(10, 5*((iter+1)/maxiter-1));
			p_d = 0.1*P;
			//开始第iter代的差分进化
			for(int i=0;i<population_size;i++) {
				
				in_s=false;
				percent = (double)i/population_size;
				if(percent<=P)in_s=true;
				if(!in_s&&first_in_i==-1)first_in_i=iter;
				if(select(i,s_gt,in_s)) sum++;
				
			}
			SR_G[iter] = sum/population_size;
			//System.out.println(SR_G[iter]);
			if(s_gt==true&&iter==g_t)s_gt=false;
			
			
			
			
//////			//预测训练样本
//			gene=this.get_result();
//			NBSVM_SMO[] ovo;
//			ovo= new NBSVM_SMO[train_data_x.length];
//			for(int i=0;i<train_data_x.length;i++) {
//				if(gene[i][4]==0)kernel="line";
//				else if(gene[i][4]==1)kernel="rbf";
//				else if(gene[i][4]==2)kernel="poly";
//				ovo[i] = new NBSVM_SMO(train_data_x[i],train_data_y[i],tol, gene[i][0],gene[i][1],gene[i][2],gene[i][3], kernel,gene[i][5],(int)gene[i][6]);}
//			double[] result = new double[validation_data_x.length];
//			for(int n=0;n<validation_data_x.length;n++) {
//				double q = 0;
//				for(int m=0;m<validation_data_x[n].length;m++)if(validation_data_y[n]==this.predict(ovo,validation_data_x[n][m]))q++;
//				result[n] = q/validation_data_x[n].length;
////				System.out.println("预测的类别:"+validation_data_y[n]);
////				System.out.println("预测的类别的向量总数:"+validation_data_x[n].length);
////				System.out.println("预测准确率"+result[n]);
//			}
//			double finally_result = 0;
//			for(int n=0;n<result.length;n++)finally_result+=result[n];
//			finally_result=finally_result/result.length;
////			System.out.println("第"+iter+"次测试结果"+finally_result);
////			System.out.println(Arrays.deepToString(gene));
//			
//
//			
////			//预测test样本
////			gene=this.get_result();
////			NBSVM_SMO[] ovo;
////			ovo= new NBSVM_SMO[train_data_x.length];
////			for(int i=0;i<train_data_x.length;i++) {
////				if(gene[i][4]==0)kernel="line";
////				else if(gene[i][4]==1)kernel="rbf";
////				else if(gene[i][4]==2)kernel="poly";
////				ovo[i] = new NBSVM_SMO(train_data_x[i],train_data_y[i],tol, gene[i][0],gene[i][1],gene[i][2],gene[i][3], kernel,gene[i][5],(int)gene[i][6]);}
////			double[] result = new double[x1.length];
//			for(int n=0;n<x1.length;n++) {
//				double q = 0;
//				for(int m=0;m<x1[n].length;m++)if(y1[n]==this.predict(ovo,x1[n][m]))q++;
//				result[n] = q/x1[n].length;
//				System.out.println("预测的类别:"+y1[n]);
//				System.out.println("预测的类别的向量总数:"+x1[n].length);
//				System.out.println("预测准确率"+result[n]);
//			}
//			finally_result = 0;
//			for(int n=0;n<result.length;n++)finally_result+=result[n];
//			finally_result=finally_result/result.length;
//			System.out.println("第"+iter+"次测试结果"+finally_result);
//			System.out.println(Arrays.deepToString(this.get_result()));
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
//			System.out.println(distance);
			
			iter++;
		}
	}
	
	//平均error
//	private double get_loss(double[] data_x,Integer data_y) {
//		Integer positive,negative;
//		double error=0;
//		double probability;
//		for(int i=0;i<ovo.length;i++) {
//			negative = ovo[i].get_label()[0];
//			positive = ovo[i].get_label()[1];
//			if(data_y==negative||data_y==positive) {
//				probability = ovo[i].get_probability(data_x);
//				if(data_y==positive)error+=1-probability;
//				else error+=probability;
//				error+=Math.sqrt((Math.log(ovo[i].getNumber())+Math.log(20))/(2*train_data_x[i].length));
//			}
//		}
//		error=error/(validation_data_y.length-1);
//		return error;
//	}
	
	//最大error
	private double get_loss(double[] data_x,Integer data_y) {
		Integer positive,negative;
		double error;
		double probability;
		double result=0;
		for(int i=0;i<ovo.length;i++) {
			error=0;
			negative = ovo[i].get_label()[0];
			positive = ovo[i].get_label()[1];
			if(data_y==negative||data_y==positive) {
				probability = ovo[i].get_probability(data_x);
				if(data_y==positive)error+=1-probability;
				else error+=probability;
				error+=Math.sqrt((Math.log(ovo[i].getNumber())+Math.log(20))/(2*train_data_x[i].length));
			}
			if(error>result)result=error;
		}
		return result;
	}
	
	
	//二分类器error
	private double fitness(double[][] gen) {
		double result=0;
		double error;
		for(int i=0;i<train_data_x.length;i++) {
			if(gen[i][4]==0)kernel="line";
			else if(gen[i][4]==1)kernel="rbf";
			else if(gen[i][4]==2)kernel="poly";
			ovo[i] = new NBSVM_SMO(train_data_x[i],train_data_y[i],tol, gen[i][0],gen[i][1],gen[i][2],gen[i][3], kernel,gen[i][5],(int)gen[i][6]);
		}
		for(int i=0;i<validation_data_x.length;i++) {
			error=0;
			for(int j=0;j<validation_data_x[i].length;j++) {
				error+=get_loss(validation_data_x[i][j],validation_data_y[i]);
			}
			result+=error/validation_data_x[i].length;
		}
		result/=validation_data_y.length;
		
		
		return result;
	}
	
	
	//总体error
//	private double fitness(double[][] gen) {
//		NBSVM_SMO[] ovo = new NBSVM_SMO[train_data_x.length];
//		Integer positive;
//		Integer negative;
//		double support_sum=0;
//		double result=0;
//		double probability;
//		double error;
//		double error_sum=0;
//		double[] value;
//		double max;
//		double min;
//		for(int i=0;i<train_data_x.length;i++) {
//			error_sum=0;
////			sum_rank_negative=0;
//			if(gen[i][4]==0)kernel="line";
//			else if(gen[i][4]==1)kernel="rbf";
//			else if(gen[i][4]==2)kernel="poly";
//			ovo[i] = new NBSVM_SMO(train_data_x[i],train_data_y[i],tol, gen[i][0],gen[i][1],gen[i][2],gen[i][3], kernel,gen[i][5],(int)gen[i][6]);
////			System.out.print(ovo[i].getNumber()+" ");
//			support_sum+=ovo[i].getNumber();
////			System.out.println("-----------------------");
//		}
////		System.out.println();
//		for(int i=0;i<validation_data_x.length;i++) {
//			for(int j=0;j<validation_data_x[i].length;j++) {
//				value = new double[validation_data_y.length];
//				for(int z=0;z<ovo.length;z++) {
//					negative = ovo[z].get_label()[0];
//					positive = ovo[z].get_label()[1];
//					probability=ovo[z].get_probability(validation_data_x[i][j]);
//					for(int p=0;p<validation_data_y.length;p++) {
//						if(positive==validation_data_y[p])value[p]+=probability;
//						if(negative==validation_data_y[p])value[p]+=1-probability;
//					}
//				}
//				//归一化
//				max=value[0];
//				min=value[0];
//				for(int p=1;p<value.length;p++) {
//					if(value[p]>max)max=value[p];
//					if(value[p]<min)min=value[p];
//				}
//				for(int p=0;p<value.length;p++) {
//					if(max-min==0)value[p]=0;
//					else value[p]=(value[p]-min)/(max-min);
//				}
//				//计算error
//				error=1-value[i];
//				error_sum+=error;
//			}
//			
//		}
//		result=error_sum/sum_training_data+Math.sqrt((Math.log(support_sum)+Math.log(20))/(2*sum_training_data));
////		System.out.println(result);
//		
//		
//		return result;
//	}
	
	
//	//最高票数相同的时候在所有最高票数的类中随机选取
//	public Integer predict(NBSVM_SMO[] ovo,double[] unkonwn) {
//		double value[] = new double[validation_data_y.length];
//		Vector<Integer> index_list = new Vector<Integer>();
//		double percent;
//		Integer[] label;
//		int p=0;
//		for(int i=0;i<ovo.length;i++) {
//			percent = ovo[i].get_probability(unkonwn);
//			label=ovo[i].get_label();
//			for(int j=0;j<validation_data_y.length;j++) {
//				if(label[0]==validation_data_y[j])value[j]+=1-percent;
//				if(label[1]==validation_data_y[j])value[j]+=percent;
//			}
//		}
////		System.out.println(Arrays.toString(value));
//		for(int i=1;i<value.length;i++)if(value[i]>value[p])p=i;
////		for(int i=0;i<value.length;i++)if(value[i]==value[p]&&validation_data_x[i].length<validation_data_x[p].length)p=i;
////		for(int i=0;i<value.length;i++)if(value[i]==value[p]&&validation_data_x[i].length==validation_data_x[p].length)index_list.add(i);
////		p=new Random().nextInt(index_list.size());
////		p=index_list.get(p);
//		return validation_data_y[p];
//	}
	//最高票数相同的时候在所有最高票数的类中随机选取
	public Integer predict(NBSVM_SMO[] ovo,double[] unkonwn) {
		int value[] = new int[validation_data_y.length];
		Vector<Integer> index_list = new Vector<Integer>();
		Integer result;
		double percent;
		Integer[] label;
		int p=0;
		for(int i=0;i<ovo.length;i++) {
			percent = ovo[i].get_probability(unkonwn);
			label=ovo[i].get_label();
			if(percent>=0.5)result=label[1];
			else result=label[0];
			//result=ovo[i].predict(unkonwn);
			for(int j=0;j<validation_data_y.length;j++)if(result==validation_data_y[j])value[j]++;
		}
		for(int i=1;i<value.length;i++)if(value[i]>value[p])p=i;
		for(int i=0;i<value.length;i++)if(value[i]==value[p]&&validation_data_x[i].length<validation_data_x[p].length)p=i;
		for(int i=0;i<value.length;i++)if(value[i]==value[p]&&validation_data_x[i].length==validation_data_x[p].length)index_list.add(i);
		p=new Random().nextInt(index_list.size());
		p=index_list.get(p);
		return validation_data_y[p];
	}
	
	
	//变异操作,对i个个体的所有基因进行变异
	private double[][] mutation(int rx,boolean s_gt,boolean in_s) {
		int r0=rx;
		int r1=rx;
		int r2=rx;
		int r3=rx;
		Random randoms = new Random();
		double[][] v = new double[train_data_x.length][features_size];
		double[][] d_r3 = new double[train_data_x.length][features_size];
		while(r1==rx)r1 = randoms.nextInt(population_size);
		if(!in_s)while(r1==rx)r1 = randoms.nextInt(first_in_i+1);
		while(r0==rx||r0==r1)r0 = randoms.nextInt(population_size);
		while(r2==rx||r2==r0||r2==r1)r2 = randoms.nextInt(population_size);
		while(r3==rx||r3==r0||r3==r1||r3==r2)r3 = randoms.nextInt(population_size);
		
		//这里的flag对应论文的g ≤ gt. true的时候Set o = i；
		if(s_gt)r0=rx;
		F = new Random().nextGaussian()*Math.sqrt(0.1)+((double)r0/population_size);
		for(int k=0;k<train_data_x.length;k++) {
			for(int i=0;i<features_size;i++) {
				if(randoms.nextDouble()<p_d) {
					
					if(i==4||i==6) {d_r3[k][i] = (int)random.nextInt((int)x_u[i]+1)-random.nextInt((int)x_l[i]+1);}
					else {d_r3[k][i] = d_r3[k][i] = x_l[i]+randoms.nextDouble()*(x_u[i]-x_l[i]);}
					}
				else {d_r3[k][i] = population[k][r3][i];}
			}
		}
		
		for(int k=0;k<train_data_x.length;k++) {
			for(int i=0;i<features_size;i++) {
				if(i==4||i==6) {v[k][i] =population[k][r0][i]+population[k][r1][i]-population[k][r0][i]+population[k][r2][i]-d_r3[k][i];continue;}
				else v[k][i] =population[k][r0][i]+F*(population[k][r1][i]-population[k][r0][i]) +F*(population[k][r2][i]-d_r3[k][i]);
			}
		}
		return v;
	}
	
	private double[][] crossover(int i,boolean s_gt,boolean in_s) {
		double[][] v = mutation(i,s_gt,in_s);
		double[][] u = new double[train_data_x.length][features_size];
		Random randoms = new Random();
		CR = randoms.nextGaussian()*Math.sqrt(0.1)+((double)i/population_size);
		int j = randoms.nextInt(features_size);
		int l = randoms.nextInt(train_data_x.length);
		double p;
		for(int n=0;n<train_data_x.length;n++) {
			for(int k=0;k<features_size;k++) {
				p = randoms.nextDouble();
				if(p<=CR || (k==j&&n==l))u[n][k] = v[n][k];
				else u[n][k] = population[n][i][k];
				if(k==4&&(u[n][k]<x_l[k]||u[n][k]>x_u[k]))u[n][k]=(int)random.nextInt((int)x_u[k]+1)-random.nextInt((int)x_l[k]+1);
				else if(u[n][k]<x_l[k]&&k==6)u[n][k] = (int)random.nextInt((int)x_u[k]+1)-random.nextInt((int)x_l[k]+1);
				else if(u[n][k]<x_l[k])u[n][k] = x_l[k]+p*(x_u[k]-x_l[k]);
				else if(u[n][k]>x_u[k]&&(k==2||k==3))u[n][k] = x_l[k]+p*(x_u[k]-x_l[k]);
//				System.out.println(u[n][4]);
//				System.out.println(u[n][6]);
//				System.out.println("------------");
			}
		}
		return u;
	}
	
	private boolean select(int i,boolean s_gt,boolean in_s) {
		boolean is_replace = false;
		double[][] u = crossover(i,s_gt,in_s);
		double cost_0 = cost[i];
		double cost_1 = fitness(u);
		if(cost_1<cost_0) {
			is_replace = true;
			cost[i]=cost_1;
			for(int m=0;m<train_data_x.length;m++) 
				for(int n=0;n<features_size;n++)population[m][i][n] = u[m][n];
			if(cost_1<distance) {tar=u.clone();distance = cost_1;}//flag=true;}
		}
		return is_replace;
	}
	
}
