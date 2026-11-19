#!/usr/bin/env python3
"""Offline, narrowly scoped storyPoints correction experiment; never edits source CSV."""
import csv,json,re,pathlib,collections
ROOT=pathlib.Path(__file__).parent
SRC=ROOT/'main_validation_results(3).csv'
OUT=ROOT/'storypoints_correction_experiment.csv'
JSONOUT=ROOT/'storypoints_corrected_outputs.json'
IDS=['M011','M013','M014','M016','M027','M034','M048','M050']
rows=list(csv.DictReader(SRC.open(encoding='utf-8-sig',newline='')))
selected={}
for row in rows:
 if row['input_id'] in IDS and row['http_status']=='200' and row['attempt']=='retry':
  if row['input_id'] in selected: raise ValueError('Duplicate retry HTTP 200: '+row['input_id'])
  selected[row['input_id']]=row
if set(selected)!=set(IDS): raise ValueError('Missing retry rows: '+str(set(IDS)-set(selected)))
def parse(s):
 s=s.strip()
 if s.startswith('```'): s=re.sub(r'^```(?:json)?\s*','',s); s=re.sub(r'\s*```$','',s)
 return json.loads(s)
def valid(obj):
 if not isinstance(obj,dict):return False
 if not all(isinstance(obj.get(k),str) and obj[k].strip() for k in ('role','goal','benefit')):return False
 for k in ('acceptanceCriteria','dependencies'):
  v=obj.get(k)
  if v is not None and not (isinstance(v,str) or isinstance(v,list) and all(isinstance(x,str) for x in v)):return False
 if obj.get('priority') is not None and not isinstance(obj['priority'],str):return False
 v=obj.get('storyPoints')
 return v is None or (type(v) is int and 0<v<=2147483647)
def format_story(obj):
 role=obj['role'].strip()
 if not re.match(r'^as\s+an?\s+.+',role,re.I|re.S): role='As a '+role
 goal=re.sub(r'^I\s+want\s+','',obj['goal'].strip(),flags=re.I)
 benefit=re.sub(r'^so\s+that\s+','',obj['benefit'].strip(),flags=re.I)
 benefit=re.sub(r'[.!?]+$','',benefit).strip()
 return role+', I want '+goal+', so that '+benefit+'.'
result=[]; corrected={}
for id in IDS:
 row=selected[id]; obj=parse(row['generated_content']); before=obj.get('storyPoints'); old_valid=valid(obj)
 if old_valid or row['schema_valid'].lower()!='false':raise ValueError('Unexpected baseline '+id)
 if not isinstance(before,str):raise ValueError('Unexpected type '+id)
 updated=dict(obj)
 if before.strip()=='':
  updated.pop('storyPoints'); action='omit_empty_optional_field'; after=None
 elif re.fullmatch(r'[1-9][0-9]*',before.strip()) and int(before.strip())<=2147483647:
  updated['storyPoints']=int(before.strip());action='convert_numeric_string_to_integer';after=updated['storyPoints']
 else:raise ValueError('Unsupported value '+id+' '+repr(before))
 is_valid=valid(updated)
 if not is_valid:raise ValueError('Correction failed '+id)
 story=format_story(updated)
 corrected[id]=updated
 result.append({'input_id':id,'attempt':'offline_type_correction','original_storyPoints':json.dumps(before),'corrected_storyPoints':json.dumps(after),'correction_action':action,'baseline_schema_valid':str(old_valid).lower(),'corrected_json_valid':'true','corrected_required_complete':'true','corrected_schema_valid':str(is_valid).lower(),'builder_attempted':'true','construction_success':'true','constructed_story':story})
with OUT.open('w',encoding='utf-8',newline='') as f:
 writer=csv.DictWriter(f,fieldnames=list(result[0]));writer.writeheader();writer.writerows(result)
JSONOUT.write_text(json.dumps(corrected,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print('Selected:',len(result),'Baseline valid:',sum(r['baseline_schema_valid']=='true' for r in result),'Corrected valid:',sum(r['corrected_schema_valid']=='true' for r in result),'Constructed:',sum(r['construction_success']=='true' for r in result))
print('Actions:',dict(collections.Counter(r['correction_action'] for r in result)))
print('Files:',OUT,JSONOUT)
