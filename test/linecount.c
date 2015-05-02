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
	int totallines=0;
	int i,t;
	int fin;
	for (i=1;i<argc;i++){
		fin=open(argv[i]);
		while (freadint(fin,&t))
			totallines++;
	}
	return totallines;
}
