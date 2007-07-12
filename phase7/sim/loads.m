clear all;

printeps = 1;
% Direct scaling for conversion
%set(0,'defaultaxesfontsize',24);
%set(0,'defaulttextfontsize',24);
  
N = 5000;
d = 50;
D = load(sprintf('out_%d_%d_nolocals_undir',N,d));
directed = 0;

maxlen = N/2;

edge_recv = D(:,3);
edge_sent = D(:,4);
edge_loads = edge_recv + edge_sent;

edgeloaddists = zeros(max(edge_loads)+1,1);
edgeloadnums = zeros(max(edge_loads)+1,1);
degrees = zeros(N,1);
totloads = zeros(N,1);
edgedistloads = zeros(N/2,1);
edgedistnums = zeros(N/2,1);
load_lens = zeros(length(D),1);

for i=1:length(D);
    j = D(i,1) + 1;
    src = D(i,1);
    dst = D(i,2);
    inload = edge_recv(i);    %%% Load put on node through this edge
    edgeload = edge_loads(i); %%% Total load on edge

    %%% Total load and degree for nodes
    totloads(j) = totloads(j) + inload;
    if(directed) 
      if(edge_sent(i)>0)
        degrees(j) = degrees(j) + 1;
      end
    else
      degrees(j) = degrees(j) + 1;
    end

    %%% Length + help index
    len = min(max(src,dst)-min(src,dst),min(src,dst)+N-max(src,dst));
    li = len + 1;

    %%% Load of lengths, count number of edges with this length    
    edgedistloads(li) = edgedistloads(li) + edgeload;
    edgedistnums(li) = edgedistnums(li) + 1;

    %%% Length of edges
    load_lens(i) = len;

    edgeloaddists(edgeload) = edgeloaddists(edgeload) + len;
    edgeloadnums(edgeload) = edgeloadnums(edgeload) + 1;
end

nz_edgeloaddists = edgeloaddists(find(edgeloaddists>0));
nz_edgeloadnums = edgeloadnums(find(edgeloadnums>0));
nz_edgeloadindex = find(edgeloaddists>0);

nz_edgedistloads = edgedistloads(find(edgedistloads>0));
nz_edgedistnums = edgedistnums(find(edgedistnums>0));
nz_edgelenindex = find(edgedistnums>0);

%%% Node load
figure(1);
subplot(111);
hold on;
partloads = totloads;
[h,x] = hist(partloads,20);
hnorm = h/sum(h);
bar(x,hnorm);
title(sprintf('N=%d, avgdeg=%d',N,d));
xlabel(sprintf('Node load'));
ylabel('Fraction of nodes');
% For locs:
%xlim([0 90000]);
% No locs:
%xlim([0 200000]);

if(printeps>0)
    if(directed)
      print('-deps',sprintf('rep2N%dD%d_1_nodeLoadHist_directed',N,d));
    else
      print('-deps',sprintf('rep2N%dD%d_1_nodeLoadHist',N,d));
  end
end

%%% Histogram of edge loads
figure(2);
subplot(111);
hold on;
%partloads = edge_loads/mean(edge_loads);
partloads = edge_loads;
[h,x] = hist(partloads,20);
hnorm = h/sum(h);
bar(x,hnorm);
title(sprintf('N=%d, avgdeg=%d',N,d));
xlabel(sprintf('Edge load'));
ylabel('Fraction of edges');
% For locs:
%xlim([0 40000]);
% No locs:
%xlim([0 100000]);

if(printeps>0)
  if(directed)
    print('-deps',sprintf('rep2N%dD%d_2_edgeLoadHist_directed',N,d));
  else
    print('-deps',sprintf('rep2N%dD%d_2_edgeLoadHist',N,d));
  end
end


%%% Degree vs Average Load
ndeg = zeros(max(degrees),1);
degloads = zeros(max(degrees),1);
degloadall = zeros(N,1);
for i = 1:size(totloads)
    deg = degrees(i);
    ndeg(deg) = ndeg(deg) + 1;
    degloads(deg) = degloads(deg) + totloads(i);
end

figure(3);
subplot(111);
hold on;
plot(find(ndeg>0), degloads(find(degloads>0))./ndeg(find(ndeg>0)));
plot(degrees, totloads, 'k.', 'MarkerSize',2);
title(sprintf('N=%d, avgdeg=%d',N,d));
if(directed)
  xlabel('Node out-degree');
else
  xlabel('Node degree');
end
ylabel('Node load');
legend('Average load');
%ylim([0 200000]);
if(printeps>0)
  if(directed)
    print('-deps',sprintf('rep2N%dD%d_3_degAvgLoad_directed',N,d));
  else
    print('-deps',sprintf('rep2N%dD%d_3_degAvgLoad',N,d));
  end
end

%figure(4);
%hold on;
%plot(degrees, totloads, 'k.', 'MarkerSize',2);
%xlabel('Degree');
%ylabel('Node load');
%title(sprintf('N=%d, avgdeg=%d',N,d));

%%% Edge loads as function of edge dist
figure(5);
subplot(111);
hold on;
plot(load_lens/N, edge_loads,'k.','MarkerSize',2);
xlabel('Edge length');
ylabel('Edge load');
title(sprintf('N=%d, avgdeg=%d',N,d));
%ylim([0 60000]);
if(printeps>0)
  if(directed)
    print('-deps',sprintf('rep2N%dD%d_5_lengthloads_directed',N,d));
  else
    print('-deps',sprintf('rep2N%dD%d_5_lengthloads',N,d));
  end
end

%%% Average edge load as function of edge dist
figure(6);
subplot(111);
hold on;
plot(nz_edgelenindex/N, nz_edgedistloads./nz_edgedistnums, 'k.', 'MarkerSize',2);
title(sprintf('N=%d, avgdeg=%d',N,d));
xlabel('Edge length');
ylabel('Average load');
%ylim([0 12000]);
if(printeps>0)
  if(directed)
    print('-deps',sprintf('rep2N%dD%d_6_lengthAvgLoad_directed',N,d));
  else
    print('-deps',sprintf('rep2N%dD%d_6_lengthAvgLoad',N,d));
  end
end

%%% Edge load vs average distance covered
figure(7);
subplot(111);
hold on;
plot(nz_edgeloadindex,nz_edgeloaddists./nz_edgeloadnums/N,'k.', 'MarkerSize', 2);
xlabel(sprintf('Edge load'));
ylabel('Average length');
title(sprintf('N=%d, avgdeg=%d',N,d));
%ylim([0 0.5]);
%xlim([0 30000]);
if(printeps>0)
  if(directed)
    print('-deps',sprintf('rep2N%dD%d_7_loadAvgLengths_directed',N,d));
  else
    print('-deps',sprintf('rep2N%dD%d_7_loadAvgLengths',N,d));
  end
end

