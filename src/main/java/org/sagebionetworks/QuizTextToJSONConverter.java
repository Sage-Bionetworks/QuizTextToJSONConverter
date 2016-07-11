package org.sagebionetworks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.repo.model.quiz.MultichoiceAnswer;
import org.sagebionetworks.repo.model.quiz.MultichoiceQuestion;
import org.sagebionetworks.repo.model.quiz.Question;
import org.sagebionetworks.repo.model.quiz.QuestionVariety;
import org.sagebionetworks.repo.model.quiz.QuizGenerator;
import org.sagebionetworks.schema.adapter.JSONObjectAdapter;
import org.sagebionetworks.schema.adapter.org.json.JSONObjectAdapterImpl;

public class QuizTextToJSONConverter {
	private static final int NUM_QUESTIONS_PER_VARIETY = 3;
	public static final String LINK = "LINK:";
	public static final String SYNOPSIS = "SYNOPSIS:";
	 
	public static final boolean VERBOSE = true;
	
	public static void o(Object s) {System.out.println(s);}
	
	public static void main(String[] args) throws Exception {
		QuizGenerator gen = new QuizGenerator();
		gen.setId(1L);
		List<QuestionVariety> qvs = new ArrayList<QuestionVariety>();
		gen.setQuestions(qvs);
		
		InputStream is = QuizTextToJSONConverter.class.getClassLoader().getResourceAsStream("quiz.txt");
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		boolean startQuestion = true;
		boolean startQuestionVariety = true;
		List<MultichoiceAnswer> answers = null;
		long questionIndex = 0;
		long responseIndex = 0;
		int lineCount= 0;
		//String questionVarietyHeader = null;
		while (true) {
			String s = br.readLine();
			lineCount++;
			if (s==null) break;
			s = s.trim();
			if (lineCount==1) {
				// very first line
				gen.setHeader(s);
				if (VERBOSE) o("Header:\n"+s);
			} else if (s.toUpperCase().indexOf(LINK) > -1) {
				int i = s.toUpperCase().indexOf(LINK);
				String linkUrl = s.substring(i + LINK.length());
				QuestionVariety qv = qvs.get(qvs.size()-1);
				List<Question> qo = qv.getQuestionOptions();
				Question lastQuestion = qo.get(qo.size()-1);
				lastQuestion.setDocLink(linkUrl.trim());
			} else if (s.toUpperCase().indexOf(SYNOPSIS) > -1) {
				int i = s.toUpperCase().indexOf(SYNOPSIS);
				StringBuilder synopsis = new StringBuilder();
				synopsis.append(s.substring(i + SYNOPSIS.length()));
				
				//read Synopsis until we reach a blank line
				String synopsisLine = br.readLine();
				while(synopsisLine.trim().length() != 0) {
					synopsis.append(synopsisLine + "\n");
					synopsisLine = br.readLine();
				}
				
				QuestionVariety qv = qvs.get(qvs.size()-1);
				List<Question> qo = qv.getQuestionOptions();
				Question lastQuestion = qo.get(qo.size()-1);
				lastQuestion.setHelpText(synopsis.toString().trim());
				
			} else if (startQuestionVariety) {
				if (s.length()==0) continue; // extra blank line
				QuestionVariety qv = new QuestionVariety();
				qvs.add(qv);
				List<Question> questionOptions = new ArrayList<Question>();
				qv.setQuestionOptions(questionOptions);
				startQuestionVariety=false;
			} else if (startQuestion) {
				if (s.length()==0) continue; // extra blank line
				MultichoiceQuestion q = new MultichoiceQuestion();
				q.setExclusive(true);
				q.setPrompt(s);
				if (VERBOSE) o("Prompt: "+s);
				q.setQuestionIndex(questionIndex++);
				answers = new ArrayList<MultichoiceAnswer>();
				q.setAnswers(answers);
				QuestionVariety qv = qvs.get(qvs.size()-1);
				List<Question> questionOptions = qv.getQuestionOptions();
				questionOptions.add(q);
				startQuestion=false;
				responseIndex = 0;
			} else if (s.length()==0) {
				List<Question> currentQuestionVariety = qvs.get(qvs.size()-1).getQuestionOptions();
				MultichoiceQuestion currentQuestion = (MultichoiceQuestion)currentQuestionVariety.get(currentQuestionVariety.size()-1);
				if (currentQuestion.getAnswers().size()==0) {
					// extra white space preceding the first answer, continue
					continue;
				} else {
					startQuestion=true;
					QuestionVariety qv = qvs.get(qvs.size()-1);
					if (qv.getQuestionOptions().size()>=NUM_QUESTIONS_PER_VARIETY) startQuestionVariety = true;
				}
			} else {
				// it's a response
				boolean isCorrect = false;
				if (s.charAt(0)=='*') {
					isCorrect=true;
					s = s.substring(1);
				}
				MultichoiceAnswer answer = new MultichoiceAnswer();
				if (s.charAt(1)!=')') {
					char letter = (char)('a'+responseIndex);
					System.out.println("prepending "+letter+" to <"+s+">");
					s = letter+" "+s; // add 'a', 'b', etc. at front
				}
				answer.setPrompt(s); // the text already starts with a, b, c
				answer.setAnswerIndex(responseIndex);
				answer.setIsCorrect(isCorrect);
				//answer.setIsCorrect(isCorrect); // Don't know how to set this yet
				answers.add(answer);
				responseIndex++;
			}
		}
		gen.setMinimumScore((long)gen.getQuestions().size()-1);
		
		// some light validation
		if (gen.getQuestions().size()!=15) 
			throw new RuntimeException("Unexpected # of question varieties: "+gen.getQuestions().size());
		for (QuestionVariety var : gen.getQuestions()) {
			if (var.getQuestionOptions().size()!=3 && var.getQuestionOptions().size()!=1)
				throw new RuntimeException("Unexpected # of question options: "+
					var.getQuestionOptions().size());
			for  (Question q : var.getQuestionOptions()) {
				if (q.getPrompt().trim().length()==0) throw new RuntimeException("missing prompt");
				if (q instanceof MultichoiceQuestion) {
					MultichoiceQuestion mq = (MultichoiceQuestion)q;
					int correctCount = 0;
					for (MultichoiceAnswer a : mq.getAnswers()) {
						if (a.getIsCorrect()!=null && a.getIsCorrect()) correctCount++;
					}
					if (correctCount==0) throw new RuntimeException("No correct answer for "+q.getPrompt());
					mq.setExclusive(correctCount==1);
				}
			}
		}
		// now serialize the result
		JSONObjectAdapter adapter = new JSONObjectAdapterImpl();
		gen.writeToJSONObject(adapter);
		String quizGeneratorAsString = adapter.toJSONString();
		FileWriter writer = new FileWriter(new File("repository-managers.certifiedUsersTestDefault.json"));
		writer.write(quizGeneratorAsString);
		writer.close();
		System.out.println(quizGeneratorAsString);
		// format the output: http://www.freeformatter.com/json-formatter.html
	}

	
}
