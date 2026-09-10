# GAMBIT AI V12 - COLAB TRAINER (Android-compatible starter)
# Upload gambit_training.jsonl, train lightweight expert models, export ONNX.

import json, os, zipfile, math
import numpy as np
import torch
import torch.nn as nn
from google.colab import files

SYMBOLS = ["C","T","P","R","S","W","F","H","Z","L"]
IDX = {s:i for i,s in enumerate(SYMBOLS)}
FEATURE_DIM = 15
SEQ = 20
HIDDEN = 128  # intentionally smaller than original V12 for Android deployment

def features(history, i):
    x = np.zeros(FEATURE_DIM, dtype=np.float32)
    s = history[i]
    if s in IDX: x[IDX[s]] = 1
    recent = history[max(0,i-19):i+1]
    n = max(1,len(recent))
    x[10] = recent.count(s)/n
    x[11] = float(i>0 and history[i-1]==s)
    opp = {"T":"C","C":"T","W":"S","S":"W","P":"R","R":"P","L":"Z","Z":"L","F":"H","H":"F"}
    fam = {"T":"P","P":"T","C":"R","R":"C"}
    x[12] = float(i>0 and opp.get(history[i-1])==s)
    x[13] = float(i>0 and fam.get(history[i-1])==s)
    x[14] = float(s in {"S","W","F","H"})
    return x

def make_input(history):
    out=np.zeros((SEQ,FEATURE_DIM),dtype=np.float32)
    h=history[-SEQ:]
    pad=SEQ-len(h)
    start=len(history)-len(h)
    for j in range(len(h)):
        out[pad+j]=features(history,start+j)
    return out

print("Upload gambit_training.jsonl")
up = files.upload()
path = next(iter(up))
rows=[]
with open(path,"r",encoding="utf-8") as f:
    for line in f:
        if line.strip():
            try: rows.append(json.loads(line))
            except: pass

X=[]; y=[]
for r in rows:
    h=r.get("history",[])
    a=r.get("actual")
    if len(h)>=1 and a in IDX:
        X.append(make_input(h)); y.append(IDX[a])

X=torch.tensor(np.array(X),dtype=torch.float32)
y=torch.tensor(np.array(y),dtype=torch.long)
print("samples:",len(y))

class LSTMModel(nn.Module):
    def __init__(self):
        super().__init__()
        self.lstm=nn.LSTM(FEATURE_DIM,HIDDEN,2,batch_first=True,dropout=.1)
        self.fc=nn.Linear(HIDDEN,10)
    def forward(self,x):
        o,_=self.lstm(x)
        return self.fc(o[:,-1,:])

class TransformerModel(nn.Module):
    def __init__(self):
        super().__init__()
        self.emb=nn.Linear(FEATURE_DIM,HIDDEN)
        layer=nn.TransformerEncoderLayer(HIDDEN,4,dim_feedforward=HIDDEN*2,batch_first=True,dropout=.1)
        self.enc=nn.TransformerEncoder(layer,2)
        self.fc=nn.Linear(HIDDEN,10)
    def forward(self,x):
        return self.fc(self.enc(self.emb(x))[:,-1,:])

class DynamicModel(LSTMModel):
    pass

def train(model, epochs=30):
    opt=torch.optim.Adam(model.parameters(),lr=1e-3)
    lossfn=nn.CrossEntropyLoss()
    model.train()
    for ep in range(epochs):
        opt.zero_grad()
        loss=lossfn(model(X),y)
        loss.backward()
        opt.step()
        if ep%5==0: print(ep, float(loss))
    return model

models=[LSTMModel(),TransformerModel(),DynamicModel()]
names=["lstm","transformer","dynamic"]
for m,n in zip(models,names):
    m=train(m)
    m.eval()
    dummy=torch.zeros(1,SEQ,FEATURE_DIM)
    torch.onnx.export(m,dummy,f"{n}.onnx",input_names=["input"],output_names=["output"],opset_version=17)

manifest={"format":"gambit-v12-lite","sequence":SEQ,"feature_dim":FEATURE_DIM,"symbols":SYMBOLS}
with open("manifest.json","w") as f: json.dump(manifest,f,indent=2)

with zipfile.ZipFile("gambit_models.zip","w") as z:
    for n in names: z.write(f"{n}.onnx")
    z.write("manifest.json")
files.download("gambit_models.zip")
