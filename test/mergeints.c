#include "stdio.h"
int freadint(int fin,int * ret){
	int s=0,c,t=0;
	while (1){
		c=fgetc(fin);
		if (c<=0)
			break;
		if (c>='0' && c<='9'){
			t=t*10+(c-'0');
			s++;
		}else if (s>0){
			break;
		}
	}
	if (!s)
		return 0;
	ret[0]=t;
	return 1;
}
int main(int argc,char ** argv){
	int f1=open(argv[1]);
	int f2=open(argv[2]);
	int f3=creat(argv[3]);
	int last1,last2;
	int has1=freadint(f1,&last1),has2=freadint(f2,&last2);
	while (has1 || has2){
		if (has1 && (!has2 || last1<last2)){
			fprintf(f3,"%d\n",last1);
			has1=freadint(f1,&last1);
		}else if (has2){
			fprintf(f3,"%d\n",last2);
			has2=freadint(f2,&last2);
		}
	}
	return 0;
}
