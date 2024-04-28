# from http.server import HTTPServer, BaseHTTPRequestHandler
# import json
# import cgi
# import os
# from py_wake.examples import lat_long_to_site_and_boundary

# class RequestHandlerClass(BaseHTTPRequestHandler):
    
#     def _set_response(self):
#         self.send_response(200)
#         self.send_header('Content-type', 'text/html')
#         self.end_headers()
    
#     def do_GET(self):
#         self.send_response(code=200)
#         self.send_header('content-type', 'text/html')
#         self.end_headers()
        
#         self.wfile.write('koko kalle'.encode())
#         # self.aep_result()
        
        
    
#     def do_POST(self):
        
#         # if self.path.endswith('/stuff'):
#         datatype, pdict = cgi.parse_header(self.headers.get('content-type'))
        
#         # if datatype != 'text/html': #'application/json'
#         #     self.send_response(400)
#         #     self.end_headers()
#         #     return
#         size = int(self.headers.getheader('content-length'))
#         dataMsg = json.loads(self.rfile.read(size))
#         print(dataMsg)
#         dataMsg['received'] ='OK'
        
#         # self.send_header('content-type', 'text/html')
        
#         self._set_response()
#         self.wfile.write("POST request for {}".format(self.path).encode('utf-8'))
        
    
#     def aep_result(s, *args):
#         jsonfile = 'C:/Users/Mazlomak/PyWake Environment/NewPyWake/Lib/site-packages/py_wake/examples/WindfarmSimulationData.json'
#         with open(jsonfile, 'w') as filetooverwrite:
#             filetooverwrite.write(str(s.address_string()))
            
#         # aep_result = lat_long_to_site_and_boundary.runAEPcalc()
#         # return aep_result
        


# def main():
#     PORT = 9000
#     server = HTTPServer(('',PORT), RequestHandlerClass)
#     print('Server running on port %s' % PORT)
#     server.serve_forever()
    
# if __name__ == '__main__':
#     main()